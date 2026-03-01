package model;

/**
 * 人物の共通属性を持つ抽象クラス
 */
public abstract class Person {
    protected String name;
    protected int age;
    protected String nationality;

    public Person(String name, int age, String nationality) {
        this.name = name;
        this.age = age;
        this.nationality = nationality;
    }

    // ゲッター
    public String getName() { return name; }
    public int getAge() { return age; }
    public String getNationality() { return nationality; }

    // セッター
    public void setName(String name) { this.name = name; }
    public void setAge(int age) { this.age = age; }

    /**
     * 1シーズン経過（誕生日処理など）
     */
    public void ageing() {
        this.age++;
    }

    @Override
    public String toString() {
        return name + " (" + age + "歳, " + nationality + ")";
    }
}
