package com.example.udriBook.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MobileNumberValidator.class)
@Documented
public @interface ValidMobileNumber {
    
    String message() default "Mobile number must be exactly 10 digits";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
}
