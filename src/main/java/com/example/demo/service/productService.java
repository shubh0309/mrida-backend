package com.example.demo.service;

import com.example.demo.model.Product;
import com.example.demo.repo.productRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class productService {

    @Autowired
    private productRepo repo;

    public List<Product> getAllProduct() {
        return repo.findAll();
    }
}
