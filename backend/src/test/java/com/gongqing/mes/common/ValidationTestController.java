package com.gongqing.mes.common;

import com.gongqing.mes.common.api.PageQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/test/validation")
class ValidationTestController {

    @GetMapping
    String validate(@RequestParam @Min(1) int count) {
        return "valid";
    }

    @GetMapping("/page")
    String validatePage(@Valid @ModelAttribute PageQuery pageQuery) {
        return "valid";
    }

    @GetMapping("/conflict")
    String conflict() {
        throw new DataIntegrityViolationException("database detail must not leak");
    }
}
