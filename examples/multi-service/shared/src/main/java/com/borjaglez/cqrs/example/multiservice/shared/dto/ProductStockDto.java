package com.borjaglez.cqrs.example.multiservice.shared.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProductStockDto implements Serializable {

  private String productId;
  private String productName;
  private int availableStock;
}
