package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import top.dtc.data.settlement.model.Receivable;
import top.dtc.settlement.model.api.ApiHeader;
import top.dtc.settlement.model.api.ApiResponse;
import top.dtc.settlement.module.aletapay.service.AletaReconcileService;
import top.dtc.settlement.service.ReconcileProcessService;

@Log4j2
@RestController
@RequestMapping("/receivable")
public class ReceivableController {

    @Autowired
    private AletaReconcileService aletaReconcileService;

    @Autowired
    private ReconcileProcessService reconcileProcessService;

    @PostMapping(value = "/add")
    public ApiResponse addReceivable(@RequestBody Receivable receivable) {
        String errorMsg;
        try {
            log.debug("/add {}", receivable);
            reconcileProcessService.createReceivable(receivable);
            return new ApiResponse<>(new ApiHeader(true));
        } catch (Exception e) {
            log.error("Cannot add Receivable", e);
            errorMsg = e.getMessage();
        }
        return new ApiResponse<>(new ApiHeader(errorMsg));
    }

    @PostMapping(value = "/remove")
    public ApiResponse removeReceivable(@PathVariable("receivableId") Long receivableId) {
        String errorMsg;
        try {
            log.debug("/remove {}", receivableId);
            reconcileProcessService.removeReceivable(receivableId);
            return new ApiResponse<>(new ApiHeader(true));
        } catch (Exception e) {
            log.error("Cannot remove Receivable", e);
            errorMsg = e.getMessage();
        }
        return new ApiResponse<>(new ApiHeader(errorMsg));
    }

    @PostMapping(value = "/aleta")
    public ApiResponse aletaReceivable(@RequestParam("file") MultipartFile multipartFile) {
        String errorMsg;
        try {
            log.debug("/aleta {}", multipartFile.getOriginalFilename());
            aletaReconcileService.receivableReconcile(multipartFile);
            return new ApiResponse<>(new ApiHeader(true));
        } catch (Exception e) {
            log.error("Cannot aletaReceivable", e);
            errorMsg = e.getMessage();
        }
        return new ApiResponse<>(new ApiHeader(errorMsg));
    }

}