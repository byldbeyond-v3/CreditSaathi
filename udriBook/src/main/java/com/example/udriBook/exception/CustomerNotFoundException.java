package com.example.udriBook.exception;


public class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException() {
        super("No Customers Found, Please");
    }
}