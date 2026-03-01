package model;

/**
 * 移籍オファーを表すクラス
 */
public class TransferOffer {
    public enum Status { PENDING, ACCEPTED, REJECTED }

    private Club fromClub;
    private Club toClub;
    private Player player;
    private long offerAmount;
    private Status status;

    public TransferOffer(Club fromClub, Club toClub, Player player, long offerAmount) {
        this.fromClub = fromClub;
        this.toClub = toClub;
        this.player = player;
        this.offerAmount = offerAmount;
        this.status = Status.PENDING;
    }

    public void accept() {
        this.status = Status.ACCEPTED;
        fromClub.sellPlayer(player, offerAmount);
        toClub.getSquad().add(player);
        toClub.setBudget(toClub.getBudget() - offerAmount);
    }

    public void reject() {
        this.status = Status.REJECTED;
    }

    // ゲッター
    public Club getFromClub() { return fromClub; }
    public Club getToClub() { return toClub; }
    public Player getPlayer() { return player; }
    public long getOfferAmount() { return offerAmount; }
    public Status getStatus() { return status; }

    @Override
    public String toString() {
        return String.format("%s → %s: %s に ¥%,d のオファー [%s]",
                toClub.getName(), fromClub.getName(), player.getName(), offerAmount, status);
    }
}
