package com.spe.project.travelguide.main.User;

import com.spe.project.travelguide.main.Security.JwtService;
import com.spe.project.travelguide.main.dto.requests.AuthenticationRequest;
import com.spe.project.travelguide.main.dto.requests.RegistrationRequest;
import com.spe.project.travelguide.main.dto.response.AuthenticationResponse;
import com.spe.project.travelguide.main.email.EmailService;
import com.spe.project.travelguide.main.email.EmailTemplateName;
import jakarta.mail.MessagingException;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class UserService {


    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final EmailService emailService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Value("${application.mailing.frontend.activation-url}")
    private String activationUrl;

    public void register(@Valid RegistrationRequest request) throws MessagingException {
        var user = UserEntity.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .verified(false)
                .build();

        userRepository.save(user);
        sendEmailValidation(user);
    }

    private void sendEmailValidation(UserEntity user) throws MessagingException {

        var newToken = generateAndSaveActivationToken(user);
        // send email
        emailService.sendEmail(
                user.getEmail(),
                user.fullName(),
                EmailTemplateName.ACTIVATE_ACCOUNT,
                activationUrl,
                newToken,
                "Travel Guide Account Activation"
        );

    }

    private String generateAndSaveActivationToken(UserEntity user) {
        String generatedToken = generateActivationCode(6);
        var token = Token.builder()
                .token(generatedToken)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .user(user)
                .build();
        tokenRepository.save(token);
        return generatedToken;
    }

    private String generateActivationCode(int length) {
        String characters = "0123456789";
        StringBuilder codeBuilder = new StringBuilder();
        SecureRandom secureRandom = new SecureRandom();

        for(int i=0;i<length;i++){
            int randomIndex = secureRandom.nextInt(characters.length());
            codeBuilder.append(characters.charAt(randomIndex));
        }

        return codeBuilder.toString();

    }


    public AuthenticationResponse authenticate(AuthenticationRequest authenticationRequest) {

        System.out.println("hello");

        var auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        authenticationRequest.getEmail(),
                        authenticationRequest.getPassword()
                )
        );

        System.out.println("auth: "+auth);

        var claims = new HashMap<String, Object>();

        var user = ((UserEntity)auth.getPrincipal());

        System.out.println(user);

        claims.put("fullName", user.fullName());
        var jwtToken = jwtService.generateToken(claims, user);

        return AuthenticationResponse.builder().token(jwtToken).build();


    }

//    @Transactional
    public void activateAccount(String token) throws MessagingException {
        Token savedToken = tokenRepository.findByToken(token)
                // exception needs to be defined - later
                .orElseThrow(()->new RuntimeException("Invalid Token"));

        if(LocalDateTime.now().isAfter(savedToken.getExpiresAt())){
            sendEmailValidation(savedToken.getUser());
            throw new RuntimeException("Activation Token has expired. A new token has been sent to the same email address");
        }

        var user = userRepository.findById(Long.valueOf(savedToken.getUser().getId()))
                .orElseThrow(()->new UsernameNotFoundException("User not found"));

        user.setVerified(true);
        userRepository.save(user);
        savedToken.setValidatedAt(LocalDateTime.now());
        tokenRepository.save(savedToken);


    }
}
