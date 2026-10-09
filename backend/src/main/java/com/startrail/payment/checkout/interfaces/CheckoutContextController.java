package com.startrail.payment.checkout.interfaces;

import com.startrail.payment.checkout.application.CheckoutContextQueryService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
@RequestMapping("/api/v1/checkout")
public class CheckoutContextController {
    private final CheckoutContextQueryService checkoutContextQueryService;

    public CheckoutContextController(CheckoutContextQueryService checkoutContextQueryService) {
        this.checkoutContextQueryService = checkoutContextQueryService;
    }

    @GetMapping("/{opaqueToken}/context")
    public CheckoutContextResponse getContext(@PathVariable String opaqueToken) {
        return checkoutContextQueryService.getContext(opaqueToken);
    }
}
