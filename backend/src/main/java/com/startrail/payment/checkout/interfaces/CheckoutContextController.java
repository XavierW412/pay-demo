package com.startrail.payment.checkout.interfaces;

import com.startrail.payment.checkout.application.CheckoutContextQueryService;
import com.startrail.payment.checkout.application.CheckoutStatusQueryService;
import com.startrail.payment.checkout.application.CheckoutStatusRefreshService;
import com.startrail.payment.checkout.application.CheckoutReturnRefreshService;
import com.startrail.payment.checkout.application.CheckoutReturnQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checkout")
public class CheckoutContextController {
    private final CheckoutContextQueryService checkoutContextQueryService;
    private final CheckoutStatusQueryService statusQueryService;
    private final CheckoutStatusRefreshService statusRefreshService;
    private final CheckoutReturnRefreshService returnRefreshService;
    private final CheckoutReturnQueryService returnQueryService;

    public CheckoutContextController(
            CheckoutContextQueryService checkoutContextQueryService,
            CheckoutStatusQueryService statusQueryService,
            CheckoutStatusRefreshService statusRefreshService,
            CheckoutReturnRefreshService returnRefreshService,
            CheckoutReturnQueryService returnQueryService) {
        this.checkoutContextQueryService = checkoutContextQueryService;
        this.statusQueryService = statusQueryService;
        this.statusRefreshService = statusRefreshService;
        this.returnRefreshService = returnRefreshService;
        this.returnQueryService = returnQueryService;
    }

    @GetMapping("/{opaqueToken}/context")
    public CheckoutContextResponse getContext(@PathVariable String opaqueToken) {
        return checkoutContextQueryService.getContext(opaqueToken);
    }

    @GetMapping("/{opaqueToken}/status")
    public CheckoutStatusResponse getStatus(@PathVariable String opaqueToken) {
        return statusQueryService.getStatus(opaqueToken);
    }

    @PostMapping("/{opaqueToken}/refresh")
    public CheckoutStatusResponse refreshStatus(@PathVariable String opaqueToken) {
        return statusRefreshService.refresh(opaqueToken);
    }

    @PostMapping("/return/{opaqueToken}/refresh")
    public CheckoutStatusResponse refreshAfterReturn(@PathVariable String opaqueToken) {
        return returnRefreshService.refresh(opaqueToken);
    }

    @GetMapping("/return/{opaqueToken}")
    public CheckoutStatusResponse getReturnStatus(@PathVariable String opaqueToken) {
        return returnQueryService.getStatus(opaqueToken);
    }
}
