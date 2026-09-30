package com.veripay.config;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.veripay.cliente.Cliente;
import com.veripay.cliente.ClienteDtos.AltaClienteRequest;
import com.veripay.cliente.ClienteRepository;
import com.veripay.cliente.ClienteService;
import com.veripay.cliente.Curp;
import com.veripay.cliente.EstadoKyc;
import com.veripay.cuenta.CuentaService;
import com.veripay.cuenta.CuentaService.CuentaResponse;
import com.veripay.transaccion.TransaccionDtos.OperacionRequest;
import com.veripay.transaccion.TransaccionDtos.TransferenciaRequest;
import com.veripay.transaccion.TransaccionService;
import com.veripay.usuario.Rol;
import com.veripay.usuario.Usuario;
import com.veripay.usuario.UsuarioRepository;

/**
 * Crea el usuario administrador en el primer arranque y, si {@code veripay.demo-datos=true},
 * usuarios y clientes de ejemplo para poder probar la aplicación de inmediato.
 */
@Component
public class DatosIniciales implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosIniciales.class);
    private static final String SISTEMA = "sistema";

    private final VeriPayProperties props;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final ClienteRepository clienteRepository;
    private final ClienteService clientes;
    private final CuentaService cuentas;
    private final TransaccionService transacciones;

    public DatosIniciales(VeriPayProperties props, UsuarioRepository usuarios, PasswordEncoder encoder,
            ClienteRepository clienteRepository, ClienteService clientes, CuentaService cuentas,
            TransaccionService transacciones) {
        this.props = props;
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.clienteRepository = clienteRepository;
        this.clientes = clientes;
        this.cuentas = cuentas;
        this.transacciones = transacciones;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String password = props.admin().password();
        if (password == null || password.isBlank()) {
            log.warn("VERIPAY_ADMIN_PASSWORD no está definida: no se crea el usuario administrador inicial");
        } else if (!usuarios.existsByUsername(props.admin().username())) {
            usuarios.save(new Usuario(props.admin().username(), encoder.encode(password),
                    "Administrador", Rol.ADMIN));
            log.info("Usuario administrador '{}' creado", props.admin().username());
        }
        if (props.demoDatos() && clienteRepository.count() == 0) {
            cargarDemo();
        }
    }

    private void cargarDemo() {
        usuarios.save(new Usuario("analista", encoder.encode("Analista123!"), "Ana Analista", Rol.ANALISTA));
        usuarios.save(new Usuario("auditor", encoder.encode("Auditor123!"), "Aurelio Auditor", Rol.AUDITOR));

        Cliente c1 = alta("GOMA850312HDFRRN0", "Andrés", "Gómez", "Martínez", "andres.gomez@correo.mx", "5512345678");
        Cliente c2 = alta("LOHS920721MJCPRF0", "Sofía", "López", "Hernández", "sofia.lopez@correo.mx", "3311122233");
        Cliente c3 = alta("RAMC010415HNLMRRA", "Carlos", "Ramírez", "Mendoza", "carlos.ramirez@correo.mx", "8187654321");
        alta("TOVE880130MPLRLL0", "Elena", "Torres", "Vega", "elena.torres@correo.mx", "2229876543");

        for (Cliente c : new Cliente[] {c1, c2, c3}) {
            c.marcarKyc(EstadoKyc.VERIFICADO);
        }
        CuentaResponse k1 = cuentas.abrir(c1.getId(), SISTEMA);
        CuentaResponse k2 = cuentas.abrir(c2.getId(), SISTEMA);
        CuentaResponse k3 = cuentas.abrir(c3.getId(), SISTEMA);

        transacciones.depositar(new OperacionRequest(k1.clabe(), new BigDecimal("15000.00"), "Depósito inicial"), null, SISTEMA);
        transacciones.depositar(new OperacionRequest(k2.clabe(), new BigDecimal("8200.50"), "Depósito inicial"), null, SISTEMA);
        transacciones.depositar(new OperacionRequest(k3.clabe(), new BigDecimal("3000.00"), "Depósito inicial"), null, SISTEMA);
        transacciones.transferir(new TransferenciaRequest(k1.clabe(), k2.clabe(), new BigDecimal("1250.00"), "Renta"), null, SISTEMA);
        transacciones.retirar(new OperacionRequest(k3.clabe(), new BigDecimal("500.00"), "Retiro en cajero"), null, SISTEMA);

        log.info("Datos demo cargados: 3 usuarios, 4 clientes, 3 cuentas");
    }

    private Cliente alta(String curp17, String nombre, String paterno, String materno, String email, String tel) {
        String curp = curp17 + Curp.digitoVerificador(curp17);
        LocalDate nacimiento = Curp.fechaNacimiento(curp).orElseThrow();
        return clientes.alta(new AltaClienteRequest(curp, null, nombre, paterno, materno, nacimiento, email, tel),
                SISTEMA);
    }
}
