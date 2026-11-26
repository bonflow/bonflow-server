package ch.famfrehner.bonflow.api;

import java.util.Arrays;

import ch.famfrehner.bonflow.masterdata.MasterDataService;
import ch.famfrehner.bonflow.masterdata.MasterDataSnapshot;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/masterdata")
@Tag(name = "Master data")
class MasterDataController {

    private final MasterDataService masterData;

    MasterDataController(MasterDataService masterData) {
        this.masterData = masterData;
    }

    @Operation(summary = "All master data for the app; supports If-None-Match and answers 304 when unchanged")
    @GetMapping
    ResponseEntity<MasterDataSnapshot> get(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        MasterDataSnapshot snapshot = masterData.snapshot();
        String etag = "\"" + MasterDataService.versionOf(snapshot) + "\"";
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(CacheControl.noCache().cachePrivate());
        if (matches(ifNoneMatch, etag)) {
            return ResponseEntity.status(304).eTag(etag).cacheControl(CacheControl.noCache().cachePrivate()).build();
        }
        return response.body(snapshot);
    }

    private static boolean matches(String header, String etag) {
        if (header == null) {
            return false;
        }
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .map(v -> v.startsWith("W/") ? v.substring(2) : v)
                .anyMatch(v -> v.equals("*") || v.equals(etag));
    }
}
