package mx.siabuc.helpdesk;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Curated external links, not files downloaded or hosted by Helpdesk. */
@RestController
@RequestMapping("/api/official-resources")
public class OfficialResourceController {
    private final OfficialResourceService resources;
    public OfficialResourceController(OfficialResourceService resources) { this.resources=resources; }

    @GetMapping
    public List<OfficialResourceService.Resource> list(
            @RequestParam(required=false) OfficialResourceService.Kind kind,
            @RequestParam(defaultValue="") String q) {
        return resources.list(kind,q);
    }

    @GetMapping("/{id}")
    public OfficialResourceService.Resource get(@PathVariable long id) { return resources.get(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OfficialResourceService.Resource create(@RequestBody OfficialResourceService.Input input) {
        return resources.create(input);
    }

    @PutMapping("/{id}")
    public OfficialResourceService.Resource update(@PathVariable long id,
            @RequestBody OfficialResourceService.Input input) {
        return resources.update(id,input);
    }

    @PostMapping("/{id}/status")
    public OfficialResourceService.Resource status(@PathVariable long id,
            @RequestBody OfficialResourceService.StatusInput input) {
        return resources.changeStatus(id,input);
    }
}
