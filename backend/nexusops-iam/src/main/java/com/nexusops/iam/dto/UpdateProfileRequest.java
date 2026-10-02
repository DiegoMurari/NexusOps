package com.nexusops.iam.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Perfil editável pelo próprio usuário. Não carrega e-mail, papéis nem status: quem altera a conta
 * de alguém é o administrador, por UpdateUserRequest.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileRequest {

    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    @Size(max = 50)
    private String phone;

    @Size(max = 100)
    private String jobTitle;

    @Size(max = 100)
    private String department;

    /** Nulo mantém; vazio remove; senão precisa existir e estar ativa no tenant. */
    @Size(max = 36)
    private String defaultLocationId;
}
