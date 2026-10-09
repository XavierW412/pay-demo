package com.startrail.payment.simulator.application;

public class MockProductNotFoundException extends RuntimeException {
    public MockProductNotFoundException() {
        super("未找到所选的本地模拟服务");
    }
}
