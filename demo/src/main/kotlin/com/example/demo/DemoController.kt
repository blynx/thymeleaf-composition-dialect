package com.example.demo

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class DemoController {

    @GetMapping("/")
    fun index(model: Model): String {
        model.addAttribute("hello", "Hello World!")
        return "pages/demo"
    }
}
