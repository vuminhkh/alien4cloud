package alien4cloud.rest.csar;

import java.io.IOException;
import java.nio.file.Paths;

import javax.annotation.Resource;
import javax.validation.Valid;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.hibernate.validator.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import alien4cloud.audit.annotation.Audit;
import alien4cloud.component.repository.exception.CSARVersionAlreadyExistsException;
import alien4cloud.csar.services.CsarGitService;
import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.dao.model.GetMultipleDataResult;
import alien4cloud.exception.GitCloneUriException;
import alien4cloud.exception.GitNoModificationDetected;
import alien4cloud.exception.GitNotAuthorizedException;
import alien4cloud.exception.NotFoundException;
import alien4cloud.model.components.Csar;
import alien4cloud.rest.component.SearchRequest;
import alien4cloud.rest.model.RestErrorBuilder;
import alien4cloud.rest.model.RestErrorCode;
import alien4cloud.rest.model.RestResponse;
import alien4cloud.rest.model.RestResponseBuilder;
import alien4cloud.security.model.CsarGitRepository;
import alien4cloud.tosca.parser.ParsingException;
import alien4cloud.tosca.parser.ParsingResult;
import alien4cloud.utils.FileUtil;

import com.wordnik.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/rest/csarsgit")
public class CsarGitController {
    @Resource(name = "alien-es-dao")
    private IGenericSearchDAO alienDAO;

    @Resource
    private CsarGitService csarGitService;

    /**
     * Retrieve a CsarGit from the system
     *
     * @param param The unique id of the CsarGit to retrieve.
     * @return The CsarGit matching the requested id or url.
     */
    @ApiOperation(value = "Get a CSARGit in ALIEN by id.")
    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    @PreAuthorize("hasAnyAuthority('ADMIN', 'COMPONENTS_MANAGER', 'ARCHITECT')")
    @Audit
    public RestResponse<CsarGitRepository> get(@Valid @NotBlank @PathVariable String id) {
        if (id == null) {
            return RestResponseBuilder.<CsarGitRepository> builder()
                    .error(RestErrorBuilder.builder(RestErrorCode.ILLEGAL_PARAMETER).message("id cannot be null").build()).build();
        }
        CsarGitRepository csargit = alienDAO.findById(CsarGitRepository.class, id);
        if (csargit == null) {
            return RestResponseBuilder.<CsarGitRepository> builder()
                    .error(RestErrorBuilder.builder(RestErrorCode.NOT_FOUND_ERROR).message("id cannot be null").build()).build();
        }
        return RestResponseBuilder.<CsarGitRepository> builder().data(csargit).build();
    }

    /**
     * Retrieve a CsarGit from the system by this URL
     *
     * @param param The unique url of the CsarGit to retrieve.
     * @return The CsarGit matching the requested id or url.
     */
    @ApiOperation(value = "Get a CSARGit in ALIEN by url.")
    @RequestMapping(value = "/get", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('ADMIN', 'COMPONENTS_MANAGER', 'ARCHITECT')")
    @Audit
    public RestResponse<CsarGitRepository> getByUrl(@Valid @RequestBody String url) {
        if (url == null || url.isEmpty()) {
            return RestResponseBuilder.<CsarGitRepository> builder()
                    .error(RestErrorBuilder.builder(RestErrorCode.ILLEGAL_PARAMETER).message("Url cannot be null or empty").build()).build();
        }
        CsarGitRepository csargit = csarGitService.getCsargitByUrl(url);
        if (csargit == null) {
            return RestResponseBuilder.<CsarGitRepository> builder()
                    .error(RestErrorBuilder.builder(RestErrorCode.NOT_FOUND_ERROR).message("url cannot be null").build()).build();
        }
        return RestResponseBuilder.<CsarGitRepository> builder().data(csargit).build();
    }

    @ApiOperation(value = "Search csargits", notes = "Returns a search result with that contains CSARGIT matching the request.")
    @RequestMapping(value = "/search", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('ADMIN', 'COMPONENTS_MANAGER', 'ARCHITECT')")
    public RestResponse<GetMultipleDataResult<CsarGitRepository>> search(@RequestBody SearchRequest searchRequest) {
        GetMultipleDataResult<CsarGitRepository> searchResult = alienDAO.search(CsarGitRepository.class, searchRequest.getQuery(), null,
                searchRequest.getFrom(), searchRequest.getSize());
        searchResult.setData(searchResult.getData());
        return RestResponseBuilder.<GetMultipleDataResult<CsarGitRepository>> builder().data(searchResult).build();
    }

    /**
     * Retrieve a CsarGit from git
     * 
     * @param id The unique id of the CsarGit to retrieve.
     * @return The CsarGit matching the requested id.
     * @throws ParsingException
     * @throws CSARVersionAlreadyExistsException
     * @throws IOException
     * @throws GitCloneUriException
     * @throws GitNotAuthorizedException
     * @throws GitAPIException
     * @throws NoHeadException
     * @throws RevisionSyntaxException
     * @throws NotFoundException
     * @throws GitNoModificationDetected
     */
    @ApiOperation(value = "Specify a CSAR from Git and proceed to its import in Alien.")
    @RequestMapping(value = "/import/{id:.+}", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('ADMIN', 'COMPONENTS_MANAGER', 'ARCHITECT')")
    @Audit
    public RestResponse<GetMultipleDataResult<ParsingResult<Csar>>> specify(@Valid @RequestBody String param) throws CSARVersionAlreadyExistsException,
            ParsingException, IOException, GitCloneUriException, GitNotAuthorizedException, NotFoundException, RevisionSyntaxException, NoHeadException,
            GitAPIException, GitNoModificationDetected {
        if (param == null || param.isEmpty()) {
            return RestResponseBuilder.<GetMultipleDataResult<ParsingResult<Csar>>> builder()
                    .error(RestErrorBuilder.builder(RestErrorCode.ILLEGAL_PARAMETER).message("Url cannot be null or empty").build()).build();
        }
        ParsingResult<Csar>[] parsingResult = csarGitService.specifyCsarFromGit(param);
        GetMultipleDataResult<ParsingResult<Csar>> getMultipleDataResult = new GetMultipleDataResult<ParsingResult<Csar>>(
                new String[] { ParsingResult.class.toString() }, parsingResult);
        return RestResponseBuilder.<GetMultipleDataResult<ParsingResult<Csar>>> builder().data(getMultipleDataResult).build();
    }

    /**
     * Create a new CsarGit in the system
     * 
     * @param request The CsarGit to save in the system.
     * @return an the id of the created CsarGit {@link RestResponse}.
     */
    @ApiOperation(value = "Create a new CSARGit from a Git location in ALIEN.")
    @RequestMapping(method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('ADMIN', 'COMPONENTS_MANAGER', 'ARCHITECT')")
    @Audit
    public RestResponse<String> create(@Valid @RequestBody CreateCsarGitRequest request) {
        CsarGitRepository csargit = csarGitService.getCsargitByUrl(request.getRepositoryUrl());
        if (csargit != null && request.getRepositoryUrl().equals(csargit.getRepositoryUrl())) {
            return RestResponseBuilder
                    .<String> builder()
                    .error(RestErrorBuilder.builder(RestErrorCode.ILLEGAL_PARAMETER)
                            .message("An existing CSAR with the same url and repository already exists").build()).build();
        }
        if (!csarGitService.paramIsUrl(request.getRepositoryUrl()) || request.getRepositoryUrl().isEmpty() || request.getRepositoryUrl().isEmpty()
                || request.getImportLocations().isEmpty()) {
            return RestResponseBuilder.<String> builder()
                    .error(RestErrorBuilder.builder(RestErrorCode.ILLEGAL_PARAMETER).message("CSAR's data are not valid").build()).build();
        }
        String csarId = csarGitService.createGitCsar(request.getRepositoryUrl(), request.getUsername(), request.getPassword(), request.getImportLocations(),
                request.isStoredLocally());
        return RestResponseBuilder.<String> builder().data(csarId).build();
    }

    /**
     * Delete a CsarGit in the system.
     * 
     * @param id The unique id of the CsarGit to delete.
     * @return the id of the CsarGit deleted {@link RestResponse}.
     * @throws IOException
     */
    @ApiOperation(value = "Delete a CSARGit in ALIEN by id.")
    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
    @PreAuthorize("hasAnyAuthority('ADMIN', 'COMPONENTS_MANAGER', 'ARCHITECT')")
    @Audit
    public RestResponse<String> deleteCsarGit(@PathVariable String id) throws IOException {
        if (id == null) {
            return RestResponseBuilder.<String> builder().error(RestErrorBuilder.builder(RestErrorCode.ILLEGAL_PARAMETER).message("id cannot be null").build())
                    .build();
        }
        CsarGitRepository csarToDelete = csarGitService.checkIfCsarExist(id);
        if (csarToDelete != null) {
            if (csarToDelete.isStoredLocally()) {
                FileUtil.delete(Paths.get(csarToDelete.getCheckedOutLocation()));
                FileUtil.delete(Paths.get(csarToDelete.getCheckedOutLocation() + "_ZIPPED"));
            }
            alienDAO.delete(CsarGitRepository.class, id);
            return RestResponseBuilder.<String> builder().data(id).build();
        }
        return RestResponseBuilder.<String> builder().data(id)
                .error(RestErrorBuilder.builder(RestErrorCode.NOT_FOUND_ERROR).message("No csargit exists with this id").build()).build();
    }

    /**
     * Retrieve a CsarGit from the system by this URL
     *
     * @param param The unique url of the CsarGit to retrieve.
     * @return The CsarGit matching the requested id or url.
     * @throws IOException
     */
    @ApiOperation(value = "Delete a CSARGit in ALIEN by url.")
    @RequestMapping(value = "/delete/{url}", method = RequestMethod.POST)
    @PreAuthorize("hasAnyAuthority('ADMIN', 'COMPONENTS_MANAGER', 'ARCHITECT')")
    @Audit
    public RestResponse<String> deleteCsargitByUrl(@Valid @RequestBody String url) throws IOException {
        if (url == null || url.isEmpty()) {
            return RestResponseBuilder.<String> builder()
                    .error(RestErrorBuilder.builder(RestErrorCode.ILLEGAL_PARAMETER).message("url cannot be null or empty").build()).build();
        }
        String result = csarGitService.deleteCsargitByUrl(url);
        if ("not found".equals(result)) {
            return RestResponseBuilder.<String> builder().data(result)
                    .error(RestErrorBuilder.builder(RestErrorCode.NOT_FOUND_ERROR).message("No csargit exists with this url").build()).build();
        } else {
            return RestResponseBuilder.<String> builder().data(result).build();
        }
    }

    /**
     * Update an existing CsarGit by id
     * 
     * @param request The CsarGit data to update
     * @return an empty (void) rest {@link RestResponse}.
     */
    @ApiOperation(value = "Update a CSARGit by id.")
    @RequestMapping(value = "/{id}", method = RequestMethod.PUT, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('ADMIN', 'COMPONENTS_MANAGER', 'ARCHITECT')")
    @Audit
    public RestResponse<Void> update(@PathVariable String id, @RequestBody UpdateCsarGitRequest request) {
        csarGitService.update(id, request.getRepositoryUrl(), request.getUsername(), request.getPassword());
        return RestResponseBuilder.<Void> builder().build();
    }

    /**
     * Update an existing CsarGit by url
     * 
     * @param request The CsarGit data to update
     * @return an empty (void) rest {@link RestResponse}.
     */
    @ApiOperation(value = "Update a CSARGit by url.")
    @RequestMapping(value = "/update/{url}", method = RequestMethod.POST)
    @PreAuthorize("hasAnyAuthority('ADMIN', 'COMPONENTS_MANAGER', 'ARCHITECT')")
    @Audit
    public RestResponse<Void> updateByUrl(@Valid @RequestBody UpdateCsarGitWithUrlRequest request) {
        csarGitService.updateByUrl(request.getRepositoryUrlToUpdate(), request.getRepositoryUrl(), request.getUsername(), request.getPassword());
        return RestResponseBuilder.<Void> builder().build();
    }
}