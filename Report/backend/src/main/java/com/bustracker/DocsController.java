package com.bustracker;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DocsController {

    @GetMapping({"/docs", "/docs/"})
    public String docsPage() {
        return "forward:/docs/index.html";
    }
}
