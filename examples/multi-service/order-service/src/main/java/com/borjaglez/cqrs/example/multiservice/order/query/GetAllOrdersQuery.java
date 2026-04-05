package com.borjaglez.cqrs.example.multiservice.order.query;

import com.borjaglez.cqrs.naming.CqrsMessage;
import com.borjaglez.cqrs.query.Query;

@CqrsMessage(service = "order-service", module = "order", name = "get-all-orders")
public class GetAllOrdersQuery extends Query {}
