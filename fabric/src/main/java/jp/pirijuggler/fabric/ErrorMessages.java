package jp.pirijuggler.fabric;

import jp.pirijuggler.common.protocol.ErrorCode;

public final class ErrorMessages {
    private ErrorMessages() { }

    public static String japanese(ErrorCode code) {
        return switch (code) {
            case BUSY -> "処理中です。完了してから操作してください。";
            case INVALID_STATE -> "現在の状態では操作できません。";
            case NOT_ENOUGH_CREDIT -> "クレジットが足りません。";
            case ECONOMY_UNAVAILABLE -> "経済サービスを利用できません。";
            case NOT_ENOUGH_VAULT -> "所持金が足りません。";
            case NOT_ENOUGH_MEDALS -> "メダルが足りません。";
            case INVENTORY_FULL -> "インベントリに空きがありません。";
            case MACHINE_OCCUPIED -> "この台は使用中です。";
            case MACHINE_DISABLED -> "この台は利用できません。";
            case SESSION_MISMATCH -> "利用セッションが一致しません。";
            case SEQUENCE_OLD -> "受信済みの操作です。";
            case SPIN_MISMATCH -> "対象のゲームが一致しません。";
            case STOP_TOO_EARLY -> "まだリールを停止できません。";
            case ALREADY_STOPPED -> "このリールは停止済みです。";
            case NOT_OP -> "管理者権限が必要です。";
            case INVALID_ITEM -> "このアイテムは利用できません。";
            case TOKEN_REVIEW_REQUIRED -> "このメダルは確認が必要です。管理者に連絡してください。";
            case DB_ERROR -> "データの保存に失敗しました。";
            case VAULT_ERROR -> "所持金の処理に失敗しました。";
            case PROTOCOL_MISMATCH -> "通信バージョンが一致しないため利用できません。";
        };
    }
}
