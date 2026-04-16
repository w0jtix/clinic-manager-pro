package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.OrderDTO;
import com.clinic.clinicmanager.DTO.request.OrderFilterDTO;
import org.springframework.data.domain.Page;

import java.util.List;

public interface OrderService {

    OrderDTO getOrderById(Long id);

    Page<OrderDTO> getOrders(OrderFilterDTO filter, int page, int size);

    OrderDTO getOrderPreview(OrderDTO orderDTO);

    OrderDTO createOrder(OrderDTO orderDTO);

    OrderDTO updateOrder(Long id, OrderDTO orderDTO);

    void deleteOrderById(Long id);
}
