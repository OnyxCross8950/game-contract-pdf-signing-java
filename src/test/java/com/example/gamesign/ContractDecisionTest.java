package com.example.gamesign;

public final class ContractDecisionTest {
    public static void main(String[] args) {
        var approved = new ContractSignApplication.PlayerAsset("p1", "a1", "e1", "approved");
        var queued = new ContractSignApplication.PlayerAsset("p1", "a2", "e1", "queued");
        if (!ContractSignApplication.ContractDecision.approved(approved)) throw new AssertionError("approved asset must be signed");
        if (ContractSignApplication.ContractDecision.approved(queued)) throw new AssertionError("queued asset must wait");
        System.out.println("contract decision test passed");
    }
}
