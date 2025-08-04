package com.example.demo;

public class UserService {
    public void process() {
        UserContextCollector.get().ifPresent(u -> {
            u.setAge(u.getAge() + 1);
            System.out.println("用户 " + u.getName() + " 的新年龄：" + u.getAge());
        });
    }
} 