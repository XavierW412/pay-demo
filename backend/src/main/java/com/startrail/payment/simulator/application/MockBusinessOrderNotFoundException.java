package com.startrail.payment.simulator.application;

public class MockBusinessOrderNotFoundException extends RuntimeException {
    public MockBusinessOrderNotFoundException() {
        super("未找到本地模拟业务订单");
    }
}
