package com.equiprent.home;

import com.equiprent.equipment.EquipmentCategory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("categories", EquipmentCategory.values());
        return "home/index";
    }
}
