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
                .andExpect(content().string(containsString("retirada e devolução")))
                .andExpect(content().string(containsString("href=\"/css/app.css\"")))
                .andExpect(content().string(containsString("href=\"/brand/tokens.css\"")))
                .andExpect(content().string(containsString("src=\"/js/theme.js\"")))
                .andExpect(content().string(containsString("src=\"/brand/logos/equiprent-principal.svg\"")))
                .andExpect(content().string(containsString("src=\"/brand/logos/equiprent-reversa.svg\"")))
                .andExpect(content().string(containsString("Tema claro")))
                .andExpect(content().string(containsString("Tema escuro")))
                .andExpect(content().string(containsString("name=\"city\"")))
                .andExpect(content().string(containsString("href=\"/catalogo?category=TOOL\"")));
    }
}
