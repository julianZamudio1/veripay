package com.veripay.cliente;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/** Bean Validation: la CURP debe tener formato y dígito verificador correctos. */
@Documented
@Constraint(validatedBy = CurpValida.Validador.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface CurpValida {

    String message() default "CURP inválida (formato o dígito verificador)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<CurpValida, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            // @NotBlank se encarga de los nulos
            return value == null || Curp.esValida(value.trim().toUpperCase());
        }
    }
}
