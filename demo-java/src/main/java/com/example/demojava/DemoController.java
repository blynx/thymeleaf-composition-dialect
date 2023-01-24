package com.example.demojava;


import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DemoController {

    @GetMapping("/")
    String index(Model model) {
        model.addAttribute("hello", "Hello World!");
        return "pages/demo";
    }
}
