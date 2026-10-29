package com.equiprent.demo;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@Profile("demo")
public class DemoNavigationAdvice {
    @ModelAttribute("demoMode")
    public boolean demoMode() {
        return true;
    }
}
