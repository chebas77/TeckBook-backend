package com.usuario.backend.security.oauth2;

import com.usuario.backend.model.entity.Usuario;
import com.usuario.backend.service.user.UsuarioService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import com.usuario.backend.security.jwt.JwtTokenProvider;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private UsuarioService usuarioService;
    
    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {

        logger.info("🎯 OAuth2 Authentication Success iniciado");

        try {
            // Obtener datos del usuario OAuth2
            OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
            Map<String, Object> attributes = oAuth2User.getAttributes();

            logger.info("📧 Atributos OAuth2: {}", attributes);

            String email = (String) attributes.get("email");
            String name = (String) attributes.get("given_name");
            String lastName = (String) attributes.get("family_name");
            String pictureUrl = (String) attributes.get("picture");

            logger.info("👤 Usuario OAuth2: {} {} - {}", name, lastName, email);

            // 🔥 VALIDAR DOMINIO INSTITUCIONAL
            if (!email.endsWith("@tecsup.edu.pe")) {
                logger.error("❌ Dominio no permitido: {}", email);
                String errorMsg = URLEncoder.encode("Solo se permiten correos con dominio @tecsup.edu.pe", StandardCharsets.UTF_8);
                response.sendRedirect(frontendUrl + "/?error=" + errorMsg);
                return;
            }

            try {
                Usuario usuario = usuarioService.findByCorreoInstitucional(email);
                boolean isNewUser = (usuario == null);
                
                if (isNewUser) {
                    // 🆕 CREAR NUEVO USUARIO
                    logger.info("🆕 Creando nuevo usuario OAuth2: {}", email);
                    usuario = crearUsuarioOAuth2(email, name, lastName, pictureUrl);
                } else {
                    // 🔄 ACTUALIZAR USUARIO EXISTENTE
                    logger.info("🔄 Actualizando usuario existente: {}", email);
                    usuario = actualizarUsuarioOAuth2(usuario, name, lastName, pictureUrl);
                }

                // ✅ GENERAR TOKEN Y REDIRIGIR SIEMPRE A HOME
                String token = tokenProvider.generateToken(email);
                
                // 🔧 CONSTRUIR URL CON PARÁMETROS PARA EL FRONTEND
                StringBuilder redirectUrl = new StringBuilder(frontendUrl + "/home?token=" + token);
                
                // Agregar parámetros adicionales para el frontend
                if (isNewUser) {
                    redirectUrl.append("&new=true");
                }
                
                // 🔍 Verificar si necesita completar datos y agregar parámetro
                if (usuario.requiereCompletarDatos()) {
                    redirectUrl.append("&incomplete=true");
                    logger.info("📝 Usuario requiere completar datos: {}", email);
                } else {
                    logger.info("✅ Usuario con perfil completo: {}", email);
                }

                logger.info("🔀 Redirigiendo a: {}", redirectUrl.toString());
                response.sendRedirect(redirectUrl.toString());

            } catch (Exception e) {
                logger.error("❌ Error procesando usuario OAuth2: {}", e.getMessage(), e);
                String errorMsg = URLEncoder.encode("Error al procesar usuario: " + e.getMessage(), StandardCharsets.UTF_8);
                response.sendRedirect(frontendUrl + "/?error=" + errorMsg);
                return;
            }

        } catch (Exception e) {
            logger.error("❌ Error general en OAuth2 Success Handler", e);
            String errorMsg = URLEncoder.encode("Error general: " + e.getMessage(), StandardCharsets.UTF_8);
            response.sendRedirect(frontendUrl + "/?error=" + errorMsg);
        }
    }

    /**
     * 🆕 Crea un nuevo usuario OAuth2 con datos mínimos
     */
    private Usuario crearUsuarioOAuth2(String email, String name, String lastName, String pictureUrl) {
        Usuario newUser = new Usuario();
        newUser.setCorreoInstitucional(email);
        newUser.setNombre(name != null ? name : "");
        newUser.setApellidos(lastName != null ? lastName : "");
        newUser.setRol(Usuario.RolUsuario.ESTUDIANTE);
        
        // 🔧 Imagen de Google si está disponible
        if (pictureUrl != null && !pictureUrl.isEmpty()) {
            newUser.setProfileImageUrl(pictureUrl);
        }
      
        // 🔧 Valores por defecto para campos requeridos por la BD
        newUser.setDepartamentoId(1L); // Tecnología Digital por defecto
        
        // 🚨 CAMPOS QUE QUEDARÁN NULL PARA FORZAR COMPLETAR DATOS:
        // carreraId = null (se completa en el modal)
        // cicloActual = null (se completa en el modal)
        
        // ✅ CAMPOS QUE NO REQUIEREN COMPLETAR (se asignan después):
        // seccionId = null (se asigna por admin más tarde)
        // telefono = null (opcional, se puede completar en el modal)
        // direccion = null (opcional)
        // fechaNacimiento = null (opcional)
        
        return usuarioService.registrarUsuarioOAuth(newUser);
    }

    /**
     * 🔄 Actualiza usuario existente con datos de Google
     */
    private Usuario actualizarUsuarioOAuth2(Usuario usuario, String name, String lastName, String pictureUrl) {
        // Actualizar nombre y apellidos si están vacíos o han cambiado
        if (name != null && !name.isEmpty()) {
            usuario.setNombre(name);
        }
        if (lastName != null && !lastName.isEmpty()) {
            usuario.setApellidos(lastName);
        }
        
        // 🔧 Actualizar imagen solo si no tiene una personalizada
        if (pictureUrl != null && !pictureUrl.isEmpty()) {
            if (usuario.getProfileImageUrl() == null || 
                usuario.getProfileImageUrl().isEmpty() || 
                usuario.getProfileImageUrl().contains("googleusercontent.com")) {
                usuario.setProfileImageUrl(pictureUrl);
            }
        }
        
        return usuarioService.actualizarUsuario(usuario);
    }
}