package cn.net.pap.common.datastructure.lamba;

import java.io.Serializable;

/**
 * <p><strong>StudentDTO</strong> 是一个表示学生的数据传输对象（Data Transfer Object）。</p>
 *
 * <p>此类使用建造者（Builder）模式来创建不可变的学生实例。
 * 它专为演示简单的 lambda 表达式和流（Stream）操作而设计。</p>
 *
 * <ul>
 *     <li>不可变属性。</li>
 *     <li>通过 {@link Builder} 实现线程安全的实例化。</li>
 * </ul>
 * 
 * <p>使用示例：</p>
 * <pre>{@code
 * StudentDTO student = new StudentDTO.Builder()
 *         .setFirstName("John")
 *         .setLastName("Doe")
 *         .setAge(20)
 *         .setEmail("john.doe@example.com")
 *         .build();
 * }</pre>
 */
public class StudentDTO implements Serializable {

    /**
     * <p>学生的名字（First Name）。</p>
     */
    private final String firstName;

    /**
     * <p>学生的姓氏（Last Name）。</p>
     */
    private final String lastName;

    /**
     * <p>学生的年龄。</p>
     */
    private final int age;

    /**
     * <p>学生的电子邮件地址。</p>
     */
    private final String email;

    /**
     * <p>私有构造函数，强制通过 {@link Builder} 创建对象。</p>
     *
     * @param builder 包含初始化值的 {@link Builder} 对象。
     */
    private StudentDTO(Builder builder) {
        this.firstName = builder.firstName;
        this.lastName = builder.lastName;
        this.age = builder.age;
        this.email = builder.email;
    }

    /**
     * <p>获取名字。</p>
     *
     * @return 名字。
     */
    public String getFirstName() {
        return firstName;
    }

    /**
     * <p>获取姓氏。</p>
     *
     * @return 姓氏。
     */
    public String getLastName() {
        return lastName;
    }

    /**
     * <p>获取年龄。</p>
     *
     * @return 年龄。
     */
    public int getAge() {
        return age;
    }

    /**
     * <p>获取电子邮件地址。</p>
     *
     * @return 电子邮件地址。
     */
    public String getEmail() {
        return email;
    }

    /**
     * <p>{@link StudentDTO} 的 <strong>Builder</strong>（建造者）类。</p>
     *
     * <p>便于逐步创建 {@link StudentDTO} 对象。</p>
     */
    public static class Builder {
        private String firstName;
        private String lastName;
        private int age;
        private String email;

        /**
         * <p>设置名字。</p>
         *
         * @param firstName 名字。
         * @return 当前的 {@link Builder} 实例。
         */
        public Builder setFirstName(String firstName) {
            this.firstName = firstName;
            return this;
        }

        /**
         * <p>设置姓氏。</p>
         *
         * @param lastName 姓氏。
         * @return 当前的 {@link Builder} 实例。
         */
        public Builder setLastName(String lastName) {
            this.lastName = lastName;
            return this;
        }

        /**
         * <p>设置年龄。</p>
         *
         * @param age 年龄。
         * @return 当前的 {@link Builder} 实例。
         */
        public Builder setAge(int age) {
            this.age = age;
            return this;
        }

        /**
         * <p>设置电子邮件地址。</p>
         *
         * @param email 电子邮件地址。
         * @return 当前的 {@link Builder} 实例。
         */
        public Builder setEmail(String email) {
            this.email = email;
            return this;
        }

        /**
         * <p>构建最终的 {@link StudentDTO} 对象。</p>
         *
         * @return 新创建的 {@link StudentDTO} 实例。
         */
        public StudentDTO build() {
            return new StudentDTO(this);
        }
    }

}