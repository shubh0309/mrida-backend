package com.example.demo.controller;

import com.example.demo.model.Product;
import com.example.demo.service.productService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.List;

@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173", "https://mrida.online", "https://www.mrida.online"})

@RequestMapping("/api/")
public class productController {

    @Autowired
    private productService service;

    @GetMapping("/products")
    public ResponseEntity<List<Product>> allProduct(){
        return new ResponseEntity<>(service.getAllProduct(), HttpStatus.OK);
    }

}
