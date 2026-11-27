package hu.bplusn.portal.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/** A React alkalmazás útvonalai mind az index.html-t kapják (kliensoldali routing). */
@Controller
public class SpaController {

    @GetMapping({"/", "/login", "/dashboard", "/worklog"})
    public String index() {
        return "forward:/index.html";
    }

    @GetMapping("/api/health")
    @ResponseBody
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
