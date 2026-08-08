package com.vidyut.wallet.controller;

import com.vidyut.common.response.ApiResponse;
import com.vidyut.common.util.CurrentUserUtil;
import com.vidyut.wallet.dto.WalletResponse;
import com.vidyut.wallet.dto.WalletTopUpRequest;
import com.vidyut.wallet.dto.AutoRechargeRuleRequest;
import com.vidyut.wallet.dto.AutoRechargeRuleResponse;
import com.vidyut.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ev/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final CurrentUserUtil currentUser;

    @GetMapping
    public ResponseEntity<ApiResponse<WalletResponse>> getWallet() {
        return ResponseEntity.ok(ApiResponse.success(walletService.getWalletByUserId(currentUser.getCurrentAccountId())));
    }

    @PostMapping("/topup")
    public ResponseEntity<ApiResponse<WalletResponse>> topUpWallet(@Valid @RequestBody WalletTopUpRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Top up successful",
                walletService.topUpWallet(currentUser.getCurrentAccountId(), request)));
    }

    @GetMapping("/auto-recharge")
    public ResponseEntity<ApiResponse<List<AutoRechargeRuleResponse>>> getAutoRechargeRules() {
        return ResponseEntity.ok(ApiResponse.success(
                walletService.getAutoRechargeRules(currentUser.getCurrentAccountId())));
    }

    @PutMapping("/auto-recharge/{vehicleId}")
    public ResponseEntity<ApiResponse<AutoRechargeRuleResponse>> saveAutoRechargeRule(
            @PathVariable Long vehicleId,
            @Valid @RequestBody AutoRechargeRuleRequest request) {
        request.setVehicleId(vehicleId);
        return ResponseEntity.ok(ApiResponse.success("Vehicle auto-recharge updated",
                walletService.saveAutoRechargeRule(currentUser.getCurrentAccountId(), request)));
    }

    @DeleteMapping("/auto-recharge/{vehicleId}")
    public ResponseEntity<ApiResponse<AutoRechargeRuleResponse>> disableAutoRechargeRule(
            @PathVariable Long vehicleId) {
        return ResponseEntity.ok(ApiResponse.success("Vehicle auto-recharge disabled",
                walletService.disableAutoRechargeRule(currentUser.getCurrentAccountId(), vehicleId)));
    }
}
