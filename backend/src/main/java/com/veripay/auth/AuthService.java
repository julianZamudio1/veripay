package com.veripay.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veripay.auditoria.AuditoriaService;
import com.veripay.auth.AuthController.LoginRequest;
import com.veripay.auth.AuthController.LoginResponse;
import com.veripay.config.VeriPayProperties;
import com.veripay.usuario.Usuario;
import com.veripay.usuario.UsuarioRepository;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;
    private final long expiracionMinutos;

    public AuthService(AuthenticationManager authenticationManager, JwtEncoder jwtEncoder,
            UsuarioRepository usuarios, AuditoriaService auditoria, VeriPayProperties props) {
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.expiracionMinutos = props.jwt().expiracionMinutos();
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        Usuario usuario = usuarios.findByUsername(request.username()).orElseThrow();

        Instant ahora = Instant.now();
        Instant expira = ahora.plus(expiracionMinutos, ChronoUnit.MINUTES);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("veripay")
                .subject(usuario.getUsername())
                .issuedAt(ahora)
                .expiresAt(expira)
                .claim("nombre", usuario.getNombre())
                .claim("roles", List.of(usuario.getRol().name()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        auditoria.registrar(usuario.getUsername(), "LOGIN", "USUARIO", usuario.getId(), null);
        return new LoginResponse(token, expira, usuario.getUsername(), usuario.getNombre(), usuario.getRol().name());
    }
}
