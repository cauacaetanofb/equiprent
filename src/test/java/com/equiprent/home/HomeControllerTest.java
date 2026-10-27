package com.equiprent.home;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(HomeController.class)
@AutoConfigureMockMvc(addFilters = false) // Renderização isolada; segurança real é coberta em SecurityIT.
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRenderHomePageInPortuguese() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home/index"))
                .andExpect(content().string(containsString("lang=\"pt-BR\"")))
                .andExpect(content().string(containsString("EquipRent")))
                .andExpect(content().string(containsString("ainda não estão disponíveis")))
                .andExpect(content().string(containsString("href=\"/css/app.css\"")));
    }
}
