package com.uci.inbound.incoming;

import com.uci.adapter.gs.whatsapp.GSWhatsAppMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@Slf4j
@RestController
@RequestMapping(value = "/whatsapp")
public class GupShupOptIn {
    @RequestMapping(value = "/opt-in", method = RequestMethod.POST)
    public void gupShupWhatsApp(@Valid @RequestBody GSWhatsAppMessage message) throws Exception {

    }
}
