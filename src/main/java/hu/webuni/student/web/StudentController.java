package hu.webuni.student.web;

import hu.webuni.student.api.StudentControllerApi;
import hu.webuni.student.api.model.StudentDto;
import hu.webuni.student.mapper.StudentMapper;
import hu.webuni.student.model.Student;
import hu.webuni.student.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@RequiredArgsConstructor
@RestController
public class StudentController implements StudentControllerApi {

    //https://mapstruct.org/ minták !!! és pom.xml --- https://mapstruct.org/documentation/installation/

    //@Value("${upload.dir}")
    private String uploadDir;
    private final NativeWebRequest nativeWebRequest;

    private final ResourceLoader resourceLoader;

    // Constructor injection

    @Autowired
    StudentService studentService;


    @Autowired
    StudentMapper studentMapper;

    //@Autowired
    //LogEntryService logEntryService;


    @Override
    public Optional<NativeWebRequest> getRequest() {
        return Optional.of(nativeWebRequest);
    }

    @Override
    public ResponseEntity<Void> deleteStudent(Long id) {
        studentService.delete(id);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<StudentDto> getStudentById(Long id) {
        //System.out.println("Hello id");

        Student student = studentService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok(studentMapper.studentToDto(student));
    }

    @Override
    public ResponseEntity<StudentDto> modifyStudent(Long id, StudentDto studentDto) {
        Student student = studentMapper.dtoToStudent(studentDto);
        student.setId(id); // hogy tudjunk módosítani azonos iata-jút a uniqecheck ellenére
        try {
            StudentDto savedStudentDto = studentMapper.studentToDto(studentService.update(student));

            return ResponseEntity.ok(savedStudentDto);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    @Override
    public ResponseEntity<StudentDto> createStudent(StudentDto studentDto) {
        Student student = studentService.save(studentMapper.dtoToStudent(studentDto));
        return ResponseEntity.ok(studentMapper.studentToDto(student));
    }

    @Override
    public ResponseEntity<List<StudentDto>> getAllStudent() {
        //System.out.println("Hello all");
        return ResponseEntity.ok(studentMapper.studentsToDtos(studentService.findAll()));
    }

    @Override
    public ResponseEntity<List<StudentDto>> searchStudents(StudentDto example) {
        return ResponseEntity.ok(studentMapper.studentsToDtos(studentService.findStudentsByExample(studentMapper.dtoToStudent(example))));

    }

    @Override
    public ResponseEntity<String> uploadImageForStudent(Long id, String fileName, MultipartFile content) {
        if (content.isEmpty()) {
            return ResponseEntity.badRequest().body("Please upload a file");
        }

        fileName = "student_" + id + "_" + content.getOriginalFilename(); // Include student ID in the file name

        try {
            String filePath = getResourceFilePath(fileName);
            //old -> File targetFile = new File(filePath);

            // Check if an image already exists for the student
            String existingImagePath = studentService.getImageLocationForStudent(id);
            if (existingImagePath != null) {
                File existingFile = new File(existingImagePath);
                if (existingFile.exists()) {
                    boolean deleteResult = existingFile.delete(); // Delete the existing image
                    if (!deleteResult) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body("Failed to delete the existing file");
                    }
                } else {
                    // Log a message if the existing file does not exist
                    System.out.println("Existing file not found: " + existingImagePath);
                }
            }


            /* OLD start ->
            // Save the new file to the server
            try (InputStream inputStream = content.getInputStream();
                 OutputStream outputStream = new FileOutputStream(targetFile)) {

                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
            -> OLD end
            */


            //NEW uploader:

            studentService.saveImageForStudent(id,filePath, content.getInputStream());




            // Save the new file location into the database for the student
            studentService.saveImageLocationForStudent(id, filePath);

            return ResponseEntity.ok("File uploaded successfully: " + filePath);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to upload file: " + e.getMessage());
        }
    }

    // Method to get the resource file path
    public String getResourceFilePath(String fileName) {
        try {
            // Define the fixed directory path
            String directoryPath = "src/main/resources/static/images";

            // Create a Path object for the directory
            Path directory = Paths.get(directoryPath);

            // Check if the directory exists; if not, create it
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
            }

            // Resolve the file path within the directory
            Path filePath = directory.resolve(fileName);

            // Return the absolute file path as a string
            return filePath.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new RuntimeException("Error accessing resource file: " + fileName, e);
        }
    }


    @Override
    public ResponseEntity<Void> deleteImageForStudent(Long id) {
        Optional<Student> studentOptional = studentService.findById(id);
        if (studentOptional.isPresent()) {
            Student student = studentOptional.get();
            String imageLocation = student.getImageLocation();
            if (imageLocation != null) {
                File fileToDelete = new File(imageLocation);
                if (fileToDelete.exists()) {
                    try {
                        boolean deletionResult = fileToDelete.delete();
                        if (deletionResult) {
                            return ResponseEntity.ok().build(); // File deleted successfully
                        } else {
                            // Log that the file deletion failed
                            System.out.println("Failed to delete file: " + imageLocation);
                            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                        }
                    } catch (SecurityException e) {
                        // Log the security exception
                        System.out.println("Security Exception: " + e.getMessage());
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                    }
                } else {
                    // Log that the file does not exist
                    System.out.println("File not found: " + imageLocation);
                    return ResponseEntity.notFound().build();
                }
            } else {
                // Log that no image is associated with the student
                System.out.println("No image associated with student ID: " + id);
                return ResponseEntity.notFound().build();
            }
        } else {
            // Log that the student is not found
            System.out.println("Student not found for ID: " + id);
            return ResponseEntity.notFound().build();
        }
    }


    public void deleteImage() {

    }

}
