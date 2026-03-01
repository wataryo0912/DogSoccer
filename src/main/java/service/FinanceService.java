package service;

import model.Club;

/**
 * 財務管理サービスクラス
 */
public class FinanceService {

    /**
     * 週ごとの給与支払い処理
     */
    public void processWeeklySalaries(Club club) {
        long totalSalary = club.getTotalWeeklySalary();
        club.setBudget(club.getBudget() - totalSalary);
    }

    /**
     * 財務サマリーを文字列で返す
     */
    public String getFinanceSummary(Club club) {
        long budget = club.getBudget();
        long weeklySalary = club.getTotalWeeklySalary();
        long monthlySalary = weeklySalary * 4;
        long yearlySalary = weeklySalary * 52;

        return String.format(
            "=== %s 財務レポート ===\n" +
            "移籍予算:     ¥%,d\n" +
            "週給合計:     ¥%,d\n" +
            "月給合計:     ¥%,d\n" +
            "年俸合計:     ¥%,d\n" +
            "週給予算上限: ¥%,d\n" +
            "週給余裕:     ¥%,d",
            club.getName(),
            budget,
            weeklySalary,
            monthlySalary,
            yearlySalary,
            club.getWeeklySalaryBudget(),
            club.getWeeklySalaryBudget() - weeklySalary
        );
    }
}
