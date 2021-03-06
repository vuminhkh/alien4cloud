package alien4cloud.csar.services;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import alien4cloud.component.repository.exception.CSARVersionAlreadyExistsException;
import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.exception.AlreadyExistException;
import alien4cloud.exception.GitCloneUriException;
import alien4cloud.exception.GitNoModificationDetected;
import alien4cloud.exception.GitNotAuthorizedException;
import alien4cloud.exception.NotFoundException;
import alien4cloud.git.RepositoryManager;
import alien4cloud.model.components.CSARDependency;
import alien4cloud.model.components.Csar;
import alien4cloud.security.model.CsarDependenciesBean;
import alien4cloud.security.model.CsarGitCheckoutLocation;
import alien4cloud.security.model.CsarGitRepository;
import alien4cloud.tosca.ArchiveUploadService;
import alien4cloud.tosca.parser.ParsingException;
import alien4cloud.tosca.parser.ParsingResult;
import alien4cloud.utils.FileUtil;
import alien4cloud.utils.ReflectionUtil;

@Component
@Slf4j
public class CsarGitService {
    @Resource
    ArchiveUploadService uploadService;

    @Resource(name = "alien-es-dao")
    private IGenericSearchDAO alienDAO;

    @Value("${directories.alien}/${directories.upload_temp}")
    private String alienTmpUpload;

    public static final String LOCAL_DIRECTORY = "csarFromGit";

    public static final String SUFFIXE_ZIPPED = "_ZIPPED";

    /**
     * Create a CsarGitRepository in the system to store its informations
     * 
     * @param repositoryUrl The unique Git url of the CsarGitRepository
     * @param username The username of the user
     * @param password The password of the user
     * @param importLocations Locations where Csar's files are store
     * @param isStoredLocally The state of the the CsarGitRepository
     * @return The auto-generated id of the CsarGitRepository object
     */
    public String createGitCsar(String repositoryUrl, String username, String password, List<CsarGitCheckoutLocation> importLocations, boolean isStoredLocally) {
        CsarGitRepository csarGit = new CsarGitRepository();
        csarGit.setRepositoryUrl(repositoryUrl);
        csarGit.setUsername(username);
        csarGit.setPassword(password);
        csarGit.setImportLocations(importLocations);
        csarGit.setStoredLocally(isStoredLocally);
        alienDAO.save(csarGit);
        return csarGit.getId();
    }

    /**
     * Specify a Csar to import
     * 
     * @param param Can be an Url or an ID
     * @return The result of the import
     * @throws CSARVersionAlreadyExistsException
     * @throws ParsingException
     * @throws IOException
     * @throws GitCloneUriException
     * @throws GitNotAuthorizedException
     * @throws NotFoundException-
     * @throws RevisionSyntaxException
     * @throws NoHeadException
     * @throws GitAPIException
     * @throws GitNoModificationDetected
     */
    public ParsingResult<Csar>[] specifyCsarFromGit(String param) throws CSARVersionAlreadyExistsException, ParsingException, IOException,
            GitCloneUriException, GitNotAuthorizedException, GitAPIException, GitNoModificationDetected {
        CsarGitRepository csarGit = new CsarGitRepository();
        Map<String, String> locationsMap = new HashMap<String, String>();
        String data = param.replaceAll("\"", "");
        if (!paramIsUrl(data)) {
            csarGit = alienDAO.findById(CsarGitRepository.class, data);
        } else {
            csarGit = getCsargitByUrl(data);
        }
        RepositoryManager repoManager = new RepositoryManager();
        Path alienTmpPath = Paths.get(alienTmpUpload);
        if (csarGit == null) {
            throw new NotFoundException("CsarGit " + "[" + data + "] doesn't exist");
        }
        for (CsarGitCheckoutLocation location : csarGit.getImportLocations()) {
            locationsMap.put(location.getSubPath(), location.getBranchId());
        }
        return this.handleImportProcess(csarGit, alienTmpPath, repoManager, locationsMap);
    }

    /**
     * Retrieve all the zipped folder to import
     * 
     * @param checkedOutLocation The path to fetch
     * @return
     */
    private List<Path> getArchivesToUpload(String checkedOutLocation) {
        File folder = new File(checkedOutLocation);
        boolean find = false;
        File[] listSubFolders = folder.listFiles();
        List<Path> listPath = new ArrayList<Path>();
        for (int i = 0; i < listSubFolders.length; i++) {
            if (listSubFolders[i].getName().endsWith(SUFFIXE_ZIPPED)) {
                listPath.add(Paths.get(listSubFolders[i].getPath()));
                find = true;
            }
        }
        // If the archives haven't been found in the sub folder then check in the root folder (tmp)
        if (!find) {
            this.findInTmpTree(folder, listPath);
        }
        return listPath;
    }

    /**
     * Find a folder in the top-hierachie level
     * 
     * @param folder Folder to find
     * @param listPath List to fetch
     */

    private void findInTmpTree(File folder, List<Path> listPath) {
        File rootFolder = new File("/home/franck/.alien/upload/csarFromGit");
        File[] listFolders = rootFolder.listFiles();
        for (int i = 0; i < listFolders.length; i++) {
            if (listFolders[i].getName().equals(folder.getName() + SUFFIXE_ZIPPED)) {
                listPath.add(Paths.get(listFolders[i].getPath()));
            }
        }
    }

    /**
     * Process to the import workflow with multiples check
     * 
     * @param csarGit The CsarGitRepository to import
     * @param alienTmpPath Alien path to store the import's result
     * @param repoManager Util to clone or pull a repository
     * @param locationsMap Sub folders map to zip and import
     * @return result The result of the import
     * @throws CSARVersionAlreadyExistsException
     * @throws ParsingException
     * @throws IOException
     * @throws GitCloneUriException
     * @throws GitNotAuthorizedException
     * @throws NotFoundException
     * @throws RevisionSyntaxException
     * @throws NoHeadException
     * @throws GitAPIException
     * @throws GitNoModificationDetected
     */
    private ParsingResult<Csar>[] handleImportProcess(CsarGitRepository csarGit, Path alienTmpPath, RepositoryManager repoManager,
            Map<String, String> locationsMap) throws CSARVersionAlreadyExistsException, ParsingException, IOException, GitCloneUriException,
            GitNotAuthorizedException, GitAPIException, GitNoModificationDetected {
        ParsingResult<Csar>[] result = null;
        if (csarGit.isStoredLocally()) {
            // If both are false, this means the csarGit has not been imported
            if (csarGit.getLastCommitHash() == null || ("").equals(csarGit.getLastCommitHash())) {
                if (isAlreadyStoredLocally(csarGit)) {
                    result = triggerImportFromTmpFolder(csarGit.getCheckedOutLocation(), getArchivesToUpload(csarGit.getCheckedOutLocation()),
                            csarGit.isStoredLocally());
                } else {
                    String pathToReach = repoManager.createFolderAndClone(alienTmpPath, csarGit.getRepositoryUrl(), csarGit.getUsername(),
                            csarGit.getPassword(), csarGit.isStoredLocally(), locationsMap, LOCAL_DIRECTORY);
                    this.updateWithCheckedOutLocation(csarGit, pathToReach);
                    result = triggerImportFromTmpFolder(pathToReach, repoManager.getCsarsToImport(), csarGit.isStoredLocally());
                    this.updateLastCommit(result, csarGit);
                }
            } else {
                if (checkLastCommitHash(csarGit.getCheckedOutLocation(), csarGit)) {
                    throw new GitNoModificationDetected("No modifications found");
                } else {
                    Path pathToPull = Paths.get(csarGit.getCheckedOutLocation());
                    repoManager.pullRequest(pathToPull);
                    result = triggerImportFromTmpFolder(csarGit.getCheckedOutLocation(), repoManager.getCsarsToImport(), csarGit.isStoredLocally());
                    this.resetLastCommit(result, csarGit);
                }
            }
        } else {
            String pathToReach = repoManager.createFolderAndClone(alienTmpPath, csarGit.getRepositoryUrl(), csarGit.getUsername(), csarGit.getPassword(),
                    csarGit.isStoredLocally(), locationsMap, LOCAL_DIRECTORY);
            result = triggerImportFromTmpFolder(pathToReach, repoManager.getCsarsToImport(), false);
        }
        return result;
    }

    /**
     * Update CsarGitRepository's last commit if parsing result hasn't error
     * 
     * @param result Parsing result
     * @param csarGit CsarGitRepository to analyze
     * @throws IOException
     * @throws GitAPIException
     */
    private void updateLastCommit(ParsingResult<Csar>[] result, CsarGitRepository csarGit) throws IOException, GitAPIException {
        if (resetLastCommit(result, csarGit)) {
        } else {
            this.checkLastCommitHash(csarGit.getCheckedOutLocation(), csarGit);
        }
    }

    /**
     * Reset CsarGitRepository's last commit if parsing result has error
     * 
     * @param result Parsing result
     * @param csarGit CsarGitRepository to analyze
     * @return true or false
     */
    private boolean resetLastCommit(ParsingResult<Csar>[] result, CsarGitRepository csarGit) {
        if (checkIfImportResultHasError(result)) {
            this.updateWithHash(csarGit, "");
            return true;
        }
        return false;
    }

    /**
     * Check if the result of the import has errors
     * 
     * @param result Result of the import
     * @return true or false
     */
    private boolean checkIfImportResultHasError(ParsingResult<Csar>[] result) {
        for (ParsingResult<Csar> item : result) {
            if (!item.getContext().getParsingErrors().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the CsarGitRepository is stored locally in alien tmp path
     * 
     * @param csarGit CsarGitRepository to check
     * @return
     */
    private boolean isAlreadyStoredLocally(CsarGitRepository csarGit) {
        if (csarGit.getCheckedOutLocation() == null) {
            return false;
        } else {
            if (Files.exists(Paths.get(csarGit.getCheckedOutLocation()))) {
                return csarGit.getCheckedOutLocation() == null ? false : true;
            }
        }
        return false;
    }

    /**
     * Handle the import of CsarGitRepository (importLocations) in Alien
     * 
     * @param pathToReach The path to fetch where Csars have been checked out
     * @param csarsToImport The list of csars to import
     * @param isStoredLocally The state of the CsarGitRepository
     * @return An response if the statement was successful or not
     * @throws ParsingException
     * @throws CSARVersionAlreadyExistsException
     * @throws IOException
     * @throws GitAPIException
     */
    @SuppressWarnings("unchecked")
    public ParsingResult<Csar>[] triggerImportFromTmpFolder(String pathToReach, List<Path> csarsToImport, boolean isStoredLocally)
            throws CSARVersionAlreadyExistsException, ParsingException, IOException, GitAPIException {
        List<ParsingResult<Csar>> parsingResult = new ArrayList<ParsingResult<Csar>>();
        ParsingResult<Csar> result = null;
        List<CsarDependenciesBean> csarDependenciesBeanList = uploadService.preParsing(csarsToImport);
        for (CsarDependenciesBean dep : csarDependenciesBeanList) {
            if (dep.getDependencies() == null || dep.getDependencies().isEmpty()) {
                result = uploadService.upload(dep.getPath());
                parsingResult.add(result);
                removeTmpGitFolder(pathToReach, isStoredLocally);
                return parsingResult.toArray(new ParsingResult[parsingResult.size()]);
            }
        }
        this.updateDependenciesList(csarDependenciesBeanList);
        parsingResult = this.handleImportLogic(csarDependenciesBeanList, parsingResult);
        this.removeTmpGitFolder(pathToReach, isStoredLocally);
        return parsingResult.toArray(new ParsingResult[parsingResult.size()]);
    }

    /**
     * Update the CsarDependenciesBean list based on the CSARDependency found (i.e : deleted or imported
     * 
     * @param list List containing the CsarDependenciesBean
     */
    private void updateDependenciesList(List<CsarDependenciesBean> csarDependenciesBeanList) {
        for (CsarDependenciesBean csarContainer : csarDependenciesBeanList) {
            Iterator<?> it = csarContainer.getDependencies().iterator();
            while (it.hasNext()) {
                CSARDependency dependencie = (CSARDependency) it.next();
                if (lookupForDependencie(dependencie, csarDependenciesBeanList) == null) {
                    it.remove();
                }
            }
        }
    }

    /**
     * Remove the Git clone folder where the CSAR are stored
     * 
     * @param pathToReach Path to remove
     * @param isStoredLocally Status of the CsarGitRepository
     */
    private void removeTmpGitFolder(String pathToReach, boolean isStoredLocally) {
        try {
            Path path = Paths.get(pathToReach);
            Path pathZipped = Paths.get(pathToReach.concat(SUFFIXE_ZIPPED));
            if (!isStoredLocally) {
                if (Files.exists(pathZipped)) {
                    FileUtil.delete(pathZipped);
                }
                FileUtil.delete(path);
            }
        } catch (IOException e) {
            log.error("Error" + e);
        }
    }

    /**
     * Process to a lookup in the CsarDependenciesBean list to check if a CSARDependency is in the list
     * 
     * @param dependencie
     * @param list
     * @return the csarBean matching the dependency
     */
    private CsarDependenciesBean lookupForDependencie(CSARDependency dependencie, List<CsarDependenciesBean> csarDependenciesBeanList) {
        for (CsarDependenciesBean csarBean : csarDependenciesBeanList) {
            if ((dependencie.getName() + ":" + dependencie.getVersion()).equals(csarBean.getName() + ":" + csarBean.getVersion())) {
                return csarBean;
            }
        }
        return null;
    }

    /**
     * Method to import a repository based on its inter-dependencies
     * 
     * @param csarDependenciesBeanList List of the CsarGitRepository to import
     * @param parsingResult Result of the pre-process parsing
     * @return The result of the final import
     * @throws CSARVersionAlreadyExistsException
     * @throws ParsingException
     */
    private List<ParsingResult<Csar>> handleImportLogic(List<CsarDependenciesBean> csarDependenciesBeanList, List<ParsingResult<Csar>> parsingResult)
            throws CSARVersionAlreadyExistsException, ParsingException {
        ParsingResult<Csar> result = null;
        for (CsarDependenciesBean csarBean : csarDependenciesBeanList) {
            if (csarBean.getDependencies().isEmpty()) {
                result = uploadService.upload(csarBean.getPath());
                removeExistingDependencies(csarBean, csarDependenciesBeanList);
                parsingResult.add(result);
            } else {
                Iterator<?> it = csarBean.getDependencies().iterator();
                while (it.hasNext()) {
                    CSARDependency dep = (CSARDependency) it.next();
                    CsarDependenciesBean bean = lookupForDependencie(dep, csarDependenciesBeanList);
                    this.analyseCsarBean(result, bean, parsingResult, csarDependenciesBeanList);
                }
            }
        }
        return parsingResult;
    }
    
/**
 * Analyse a CsarDependenciesBean to check if it is ready to import
 * @param result Result of the pre-parsing process
 * @param bean Bean containing the CsarGitRepository informations
 * @param parsingResult Global result of the pre-parsing result
 * @param csarDependenciesBeanList
 * @throws CSARVersionAlreadyExistsException
 * @throws ParsingException
 */
    private void analyseCsarBean(ParsingResult<Csar> result, CsarDependenciesBean bean, List<ParsingResult<Csar>> parsingResult,
            List<CsarDependenciesBean> csarDependenciesBeanList) throws CSARVersionAlreadyExistsException, ParsingException {
        if (bean != null) {
            result = uploadService.upload(bean.getPath());
            parsingResult.add(result);
            removeExistingDependencies(bean, csarDependenciesBeanList);
        }
    }

    /**
     * Remove all the occurences of the dependencie when uploaded
     * 
     * @param bean Bean representing the Csar to import with detailed data
     * @param csarDependenciesBeanList The list containing the other CsarDependenciesBean
     */
    private void removeExistingDependencies(CsarDependenciesBean bean, List<CsarDependenciesBean> csarDependenciesBeanList) {
        Set<?> tmpSet;
        for (CsarDependenciesBean csarBean : csarDependenciesBeanList) {
            // To avoid concurrentmodificationexception we clone the set before fetching
            tmpSet = new HashSet(csarBean.getDependencies());
            Iterator<?> it = tmpSet.iterator();
            while (it.hasNext()) {
                CSARDependency dep = (CSARDependency) it.next();
                if ((dep.getName() + ":" + dep.getVersion()).equals(bean.getName() + ":" + bean.getVersion())) {
                    it.remove();
                }
            }
            csarBean.setDependencies(tmpSet);
        }
    }

    /**
     * Method to update a CsarGitRepository based on its unique id
     * 
     * @param id The unique id of the CsarGitRepository
     * @param repositoryUrl The unique url of the CsarGitRepository
     * @param username The username associated to the CsarGitRepository
     * @param password The password associated to the CsarGitRepository
     */
    public void update(String id, String repositoryUrl, String username, String password) {
        CsarGitRepository csarGitTo = checkIfCsarExist(id);
        if (getCsargitByUrl(repositoryUrl) == null) {
            CsarGitRepository csarGitFrom = new CsarGitRepository();
            csarGitFrom.setId(id);
            csarGitFrom.setRepositoryUrl(repositoryUrl);
            csarGitFrom.setUsername(username);
            csarGitFrom.setPassword(password);
            csarGitFrom.setCheckedOutLocation(csarGitTo.getCheckedOutLocation());
            csarGitFrom.setLastCommitHash(csarGitTo.getLastCommitHash());
            csarGitFrom.setStoredLocally(csarGitTo.isStoredLocally());
            if (csarGitTo != null) {
                ReflectionUtil.mergeObject(csarGitFrom, csarGitTo);
                alienDAO.save(csarGitTo);
            }
        } else {
            if (!password.equals(csarGitTo.getPassword()) || !username.equals(csarGitTo.getUsername())) {
                CsarGitRepository csarGitFrom = new CsarGitRepository();
                csarGitFrom.setId(id);
                csarGitFrom.setRepositoryUrl(repositoryUrl);
                csarGitFrom.setUsername(username);
                csarGitFrom.setPassword(password);
                csarGitFrom.setCheckedOutLocation(csarGitTo.getCheckedOutLocation());
                csarGitFrom.setLastCommitHash(csarGitTo.getLastCommitHash());
                csarGitFrom.setStoredLocally(csarGitTo.isStoredLocally());
                if (csarGitTo != null) {
                    ReflectionUtil.mergeObject(csarGitFrom, csarGitTo);
                    alienDAO.save(csarGitTo);
                }
            } else {
                throw new AlreadyExistException("Csar git already exists");
            }
        }
    }

    /**
     * Method to update a CsarGitRepository with multiple data
     * 
     * @param url The unique url of the CsarGitRepository
     * @param repositoryUrl The new url
     * @param username The new username
     * @param password The new password
     */
    public void updateByUrl(String url, String repositoryUrl, String username, String password) {
        CsarGitRepository csarGitTo = getCsargitByUrl(url);
        if (getCsargitByUrl(repositoryUrl) == null && (!username.equals(csarGitTo.getUsername()) || !password.equals(csarGitTo.getPassword()))) {
            CsarGitRepository csarGitFrom = new CsarGitRepository();
            csarGitFrom.setRepositoryUrl(repositoryUrl);
            csarGitFrom.setUsername(username);
            csarGitFrom.setPassword(password);
            if (csarGitTo != null) {
                csarGitFrom.setId(csarGitTo.getId());
                ReflectionUtil.mergeObject(csarGitFrom, csarGitTo);
                alienDAO.save(csarGitTo);
            }
        } else {
            throw new AlreadyExistException("Csar git already exists");
        }
    }

    /**
     * Method to update a CsarGitRepository with a hash
     * 
     * @param csarGitTo The CsarGitRepository to update
     * @param hash HashCode of the last commit
     */
    public void updateWithHash(CsarGitRepository csarGitTo, String hash) {
        csarGitTo.setLastCommitHash(hash);
        alienDAO.save(csarGitTo);
    }

    /**
     * Method to update a CsarGitRepository with a checked-out location
     * 
     * @param csarGitTo The CsarGitRepository to update
     * @param location Path where the object is cloned
     */
    public void updateWithCheckedOutLocation(CsarGitRepository csarGitTo, String location) {
        csarGitTo.setCheckedOutLocation(location);
        alienDAO.save(csarGitTo);
    }

    /**
     * Query elastic-search to retrieve a CsarGit by its url
     * 
     * @param url The unique URL of the repository
     * @return The Repository with the URL
     */
    public CsarGitRepository getCsargitByUrl(String url) {
        return alienDAO.customFind(CsarGitRepository.class, QueryBuilders.termQuery("repositoryUrl", url));
    }

    /**
     * Delete an CsarGitRepository based on its URL
     * 
     * @param url The unique URL of the repository
     * @return The URL deleted
     * @throws IOException
     */
    public String deleteCsargitByUrl(String url) throws IOException {
        CsarGitRepository csarGit = getCsargitByUrl(url);
        if (csarGit != null) {
            alienDAO.delete(CsarGitRepository.class, QueryBuilders.termQuery("repositoryUrl", url));
            return url;
        }
        throw new NotFoundException("CsarGit [" + url + "] does not exist");
    }

    /**
     * Retrieve the CsarGitRepository based on its id
     * 
     * @param id The unique id of the CsarGitRepository
     * @return A CsarGitRepository object or a a new {@link NotFoundException}.
     */
    public CsarGitRepository checkIfCsarExist(String id) {
        CsarGitRepository csarGit = alienDAO.findById(CsarGitRepository.class, id);
        if (csarGit == null) {
            throw new NotFoundException("CsarGit [" + id + "] does not exist");
        }
        return csarGit;
    }

    /**
     * Check if the CsarGitRepository is up-to-date regarding the latest commit hash
     * @param path Path of the stored location
     * @param csarGit CsarGitRepository to pull
     * @throws IOException
     * @throws GitAPIException
     * @throws RevisionSyntaxException
     *
     */
    private boolean checkLastCommitHash(String path, CsarGitRepository csarGit) throws IOException, GitAPIException {
        Git git;
        boolean isToSameState = false;
        Repository repository = new FileRepository(path + "/.git");
        git = new Git(repository);
        Iterable<RevCommit> revCommits = git.log().add(repository.resolve("master")).call();
        String hash = "";
        for (RevCommit revCommit : revCommits) {
            hash = revCommit.getName();
            break;
        }
        if (csarGit.getLastCommitHash() == null || csarGit.getLastCommitHash() == "") {
            this.updateWithHash(csarGit, hash);
        } else {
            if (csarGit.getLastCommitHash().equals(hash)) {
                isToSameState = true;
            }
        }
        return isToSameState;
    }

    /**
     * Check if the parameter is either an URL or an ID
     * 
     * @param data Data used to query the CsarGit
     * @return True is the pattern match the parameter with the regex, else false
     */
    public boolean paramIsUrl(String data) {
        String regex = "^(https?|git|http)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";
        Pattern urlPattern = Pattern.compile(regex);
        Matcher m = urlPattern.matcher(data);
        return m.matches();
    }

}