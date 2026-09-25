package jp.pirijuggler.fabric;

import jp.pirijuggler.common.protocol.ErrorCode;

public final class ErrorMessages {
    private ErrorMessages() { }

    public static String japanese(ErrorCode code) {
        return switch (code) {
            case BUSY -> "処理中です。少し待ってからもう一度お試しください。";
            case INVALID_STATE -> "今はこの操作を行えません。";
            case NOT_ENOUGH_CREDIT -> "クレジットが足りません。メダルを投入してください。";
            case ECONOMY_UNAVAILABLE -> "現在、入出金機能を利用できません。";
            case NOT_ENOUGH_VAULT -> "所持金が足りません。";
            case NOT_ENOUGH_MEDALS -> "メダルが足りません。";
            case INVENTORY_FULL -> "インベントリに空きがありません。空きを作ってからお試しください。";
            case MACHINE_OCCUPIED -> "この台はほかのプレイヤーが遊技中です。";
            case MACHINE_DISABLED -> "この台は現在利用できません。";
            case SESSION_MISMATCH -> "台との接続状態が変わりました。いったん離席して、もう一度座り直してください。";
            case SEQUENCE_OLD -> "操作が重複しました。もう一度お試しください。";
            case SPIN_MISMATCH -> "遊技状態が更新されました。もう一度お試しください。";
            case STOP_TOO_EARLY -> "まだリールを止められません。";
            case ALREADY_STOPPED -> "このリールはすでに停止しています。";
            case NOT_OP -> "この操作を行う権限がありません。";
            case INVALID_ITEM -> "このアイテムは使用できません。";
            case TOKEN_REVIEW_REQUIRED -> "このメダルは確認が必要です。管理者に連絡してください。";
            case DB_ERROR -> "処理中にエラーが発生しました。少し待ってからもう一度お試しください。";
            case VAULT_ERROR -> "所持金の処理に失敗しました。少し待ってからもう一度お試しください。";
            case PROTOCOL_MISMATCH -> "サーバーとModのバージョンが一致していません。Modを更新してください。";
        };
    }
}
