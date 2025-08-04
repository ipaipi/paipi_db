package com.example.demo;

public class Main {
    public static void main(String[] args) {
        User user = new User();
        user.setName("张三");
        user.setAge(20);

        UserContextCollector.set(user);

        UserService service = new UserService();
        service.process(); // 输出：用户 张三 的新年龄：21

        UserService service1 = new UserService();
        service1.process(); // 输出：用户 张三 的新年龄：22


        service.process(); // 输出：用户 张三 的新年龄：21
        UserContextCollector.remove();
    }
} 