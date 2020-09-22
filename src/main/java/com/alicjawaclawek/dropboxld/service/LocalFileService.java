package com.alicjawaclawek.dropboxld.service;

import com.alicjawaclawek.dropboxld.config.Constans;
import com.alicjawaclawek.dropboxld.exceptions.ConfigPropertyException;
import com.alicjawaclawek.dropboxld.model.LocalFile;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.servlet.http.Cookie;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.alicjawaclawek.dropboxld.config.Constans.*;

@Service
//@PropertySource("classpath:config.properties")

//value = "application.properties", ignoreResourceNotFound = true
public class LocalFileService {

    private static final Logger logger = LoggerFactory.getLogger(LocalFileService.class);

    //polaczenie ze sciezka skonfigurowana w config.properties
    //= "/Users/alicja/Downloads/JavaSzkolenie/Samouczek/DropboxLD/dropbox-app/files/"
    //@Value("${files.path}")
    private String uploads = "/Users/alicja/Downloads/JavaSzkolenie/Samouczek/DropboxLD/dropbox-app/files/";


    public LocalFileService() {
        try {
            createContextDirectory();
        } catch (ConfigPropertyException | IOException e) {
            e.printStackTrace();
        }
    }

    //metoda tworzaca foldery
    private void createContextDirectory() throws ConfigPropertyException, IOException {

        if (Strings.isBlank(uploads)) {
            logger.error("Cannot get path to files");
            throw new ConfigPropertyException("Cannot get path to files");
        }

        Path path = Paths.get(uploads);
        if (Files.notExists(path)) {
            try {
                //{} onacza ze wrzucamy to co po przecinku
                logger.info("Try to create directory: {}", path);
                Files.createDirectories(path);
            } catch (IOException e) {
                logger.error("Cannot create directory: {}. Exception: {}", path, e.getMessage());
                throw new IOException(e.getMessage());
            }
        }
    }

    //opakowanie odpowiedzi serwera do klienta
    public ResponseEntity<?> getFile(String fileName) {
        //resource zeby podac nazwe pliku ktory chcemy pobrac
        Resource resource;
        Path path = Paths.get(uploads + fileName);
        try {
            resource = new UrlResource(path.toUri());
        } catch (MalformedURLException e) {
            logger.error("Cannot get file: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .build();
        }
        File targetFile;

        try {
            targetFile = resource.getFile();
        } catch (IOException e) {
            logger.error("Cannot get file: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .build();
        }
        String contentType;
        try {
            contentType = Files.probeContentType(path);
        } catch (IOException e) {
            logger.error("Cannot get file: {}", e.getMessage());
            //to jest inny przyklad wykorzystania response entity
            return ResponseEntity
                    .ok()
                    .body(e.getMessage());
        }

        return ResponseEntity
                .ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=\"" + targetFile.getName() + "\"")
                .contentLength(targetFile.length())
                .body(resource);
    }

    public List<LocalFile> getFiles() {

        Stream<Path> files;
        try {
            logger.info("Try to get all files");
            files = Files.walk(Paths.get(this.uploads))
                    .filter(Files::isRegularFile);
        } catch (IOException e) {
            logger.error("Cannot get files: {}", e.getMessage());
            return null;
        }

        List<LocalFile> localFiles = new ArrayList<>();
        files.forEach(
                f -> {
                    BasicFileAttributes bs;
                    try {
                        bs = Files.readAttributes(f, BasicFileAttributes.class);
                    } catch (IOException e) {
                        logger.error("Cannot get files attributes: {}", e.getMessage());
                        return;
                    }

                    String downloadUri = ServletUriComponentsBuilder.fromCurrentContextPath().path(DOWNLOAD_URI)//sciezka do konkretnego endpointa
                            .path(f.getFileName().toString())
                            .toUriString();


                    String deleteUri = ServletUriComponentsBuilder.fromCurrentContextPath().path(DELETE_URI)//sciezka do konkretnego endpointa
                            .path(f.getFileName().toString())
                            .toUriString();

                    LocalFile localFile = new LocalFile();
                    localFile.setName(f.getFileName().toString());
                    localFile.setCreationTime(bs.creationTime().toString());
                    localFile.setLastModified(bs.lastModifiedTime().toString());
                    localFile.setSize(bs.size());
                    localFile.setDownloadUri(downloadUri);
                    localFile.setDeleteUri(deleteUri);

                    try {
                        localFile.setFileType(Files.probeContentType(f.toAbsolutePath()));
                    } catch (IOException e) {
                        logger.error("Error while getting probeContentType: {}", e.getMessage());
                    }

                    localFiles.add(localFile);
                }
        );
        return localFiles;
    }

    public ResponseEntity<String> deleteFile(String fileName) {
        File file = new File(uploads + fileName);
        if (file.delete()) {
            logger.info("Deleted file: {}", file.getName());
            return new ResponseEntity<>("Deleted file: " + file.getName(), HttpStatus.OK);
        }
        logger.error("File not found: {}", file.getName());
        return new ResponseEntity<>("File not found: " + file.getName(), HttpStatus.NOT_FOUND);
    }

    public ResponseEntity<String> uploadFile(MultipartFile file) {
        Path path = Paths.get(uploads + file.getOriginalFilename());

        try {
            logger.info("Try to get file: {}", file.getOriginalFilename());
            Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.error("Cannot get file: {} from input {}", file.getOriginalFilename(), e.getMessage());
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>(file.getOriginalFilename(), HttpStatus.CREATED);
    }
}
