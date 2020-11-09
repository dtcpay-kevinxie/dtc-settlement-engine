package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.dtc.settlement.model.api.ApiHeader;
import top.dtc.settlement.model.api.ApiResponse;
import top.dtc.settlement.module.aletapay.service.AletaReconcileService;

@Log4j2
@RestController
@RequestMapping("/reconcile")
public class ReconcileController {

    @Autowired
    private AletaReconcileService aletaReconcileService;

    @PostMapping(value = "/aleta")
    public ApiResponse aletaReconcile(@RequestParam("file") MultipartFile multipartFile) {
        String errorMsg;
        try {
            log.debug("/aleta {}", multipartFile.getOriginalFilename());
            aletaReconcileService.reconcile(multipartFile);
            return new ApiResponse<>(new ApiHeader(true));
        } catch (Exception e) {
            log.error("Cannot aletaReconcile", e);
            errorMsg = e.getMessage();
        }
        return new ApiResponse<>(new ApiHeader(errorMsg));
    }

}