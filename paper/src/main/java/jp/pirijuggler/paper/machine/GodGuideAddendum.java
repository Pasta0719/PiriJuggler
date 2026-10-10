package jp.pirijuggler.paper.machine;

import java.util.List;

/**
 * Addendum to the original 12-page GOD / EXTREME GOD in-game manuals.
 * The original signed-book pages, title, author and metadata are never rewritten.
 * No hidden heaven-entry rates, GOD continuation rates or GOD-in-GOD odds are published.
 */
public final class GodGuideAddendum {
    private static final List<String> PAGES=List.of(
            """
            小役の恩恵 追加
            ブドウ・リプレイ

            2連 高確チャンス
            3連 高確抽選15%
            4連 BONUS抽選20%
            5連 次G BONUS確定

            連続するほど
            BONUSに期待
            """.stripTrailing(),
            """
            小役の恩恵 追加
            チェリー

            1回 高確抽選8%
            2連 BONUS抽選40%
            3連 次G BONUS確定

            高確・超高確にも
            期待できる
            """.stripTrailing(),
            """
            小役の恩恵 追加
            ベル・ピエロ

            ベル成立
            次G BONUS抽選35%

            ピエロ成立
            次G BONUS抽選15%

            ハズレ時も
            高確移行に期待
            """.stripTrailing(),
            """
            高確・超高確

            高確
            最大20G
            BONUS期待度 約40%

            超高確
            最大15G
            BONUS期待度 約70%

            内部モードは非表示
            """.stripTrailing(),
            """
            BONUS当選契機

            通常BONUSの約65%は
            小役契機・高確ルート

            残りは通常抽選

            恩恵は次Gに抽選
            BONUS当選で高確終了

            GOD・天国は集計外
            """.stripTrailing()
    );

    private GodGuideAddendum() {}

    public static List<String> pages(){return PAGES;}

    public static boolean alreadyIncluded(List<String> existing) {
        return existing!=null&&existing.contains(PAGES.getFirst());
    }

    public static List<String> appended(List<String> existing) {
        if(existing==null||existing.size()<12||existing.size()+PAGES.size()>100)
            throw new IllegalArgumentException("Original 12-page GOD guide required");
        if(alreadyIncluded(existing))return List.copyOf(existing);
        var out=new java.util.ArrayList<>(existing);
        out.addAll(PAGES);
        return List.copyOf(out);
    }
}
