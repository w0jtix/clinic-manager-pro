package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.ProductDTO;
import com.clinic.clinicmanager.DTO.request.ProductFilterDTO;
import org.springframework.data.domain.Page;

import java.util.List;

public interface ProductService {

    ProductDTO getProductById(Long id);

    List<ProductDTO> getProducts(ProductFilterDTO filter);

    Page<ProductDTO> getProducts(ProductFilterDTO filter, int page, int size);

    ProductDTO createProduct(ProductDTO product);

    List<ProductDTO> createProducts(List<ProductDTO> products);

    ProductDTO updateProduct(Long id, ProductDTO product);

    void deleteProductById(Long id);
}
