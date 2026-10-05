-- La verificación KYC también lee la CURP impresa en la INE y la compara con la del cliente.
-- Las verificaciones anteriores a este cambio quedan con valores nulos (no se leyó la credencial).
ALTER TABLE verificacion_biometrica ADD COLUMN curp_ine VARCHAR(18);
ALTER TABLE verificacion_biometrica ADD COLUMN curp_coincide BOOLEAN;
