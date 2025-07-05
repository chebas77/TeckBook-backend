package com.usuario.backend.controller.aula;

import com.usuario.backend.model.entity.Anuncio;
import com.usuario.backend.service.aula.AnuncioService;
import com.usuario.backend.service.user.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/aulas/{aulaId}/anuncios")
public class AnuncioController {
    @Autowired
    private AnuncioService anuncioService;

    @Autowired
    private UsuarioService usuarioService;

    // GET: anuncios de un aula (solo para usuarios autorizados)
    @GetMapping
    public List<Anuncio> getAnunciosDeAula(@PathVariable Integer aulaId, Principal principal) {
        String email = principal.getName();
        var usuario = usuarioService.findByCorreoInstitucional(email);
        Long usuarioId = usuario.getId();
        String rol = usuario.getRol().toString(); // El rol real del usuario autenticado
        List<Anuncio> anuncios = anuncioService.getAnunciosDeAula(usuarioId, rol, aulaId);
        System.out.println("[AnuncioController] usuarioId=" + usuarioId + ", rol=" + rol + ", aulaId=" + aulaId + ", anuncios retornados=" + (anuncios != null ? anuncios.size() : 0));
        return anuncios;
    }

    // POST: crear un nuevo anuncio en un aula (con soporte para archivo y tipo)
    @PostMapping(consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<Anuncio> crearAnuncioConArchivo(
        @PathVariable Integer aulaId,
        @RequestPart("titulo") String titulo,
        @RequestPart("contenido") String contenido,
        @RequestPart("tipo") String tipo,
        @RequestPart(value = "archivo", required = false) MultipartFile archivo,
        Principal principal
    ) {
        String email = principal.getName();
        var usuario = usuarioService.findByCorreoInstitucional(email);
        Long usuarioId = usuario.getId();
        String rol = usuario.getRol().toString();
        Anuncio anuncio = new Anuncio();
        anuncio.setTitulo(titulo);
        anuncio.setContenido(contenido);
        anuncio.setTipo(tipo); // Ahora tipo es String
        if (archivo != null && !archivo.isEmpty()) {
            anuncio.setArchivoNombre(archivo.getOriginalFilename());
            anuncio.setArchivoTipo(archivo.getContentType());
            anuncio.setArchivoTamaño(archivo.getSize());
            // Aquí podrías guardar el archivo en disco o en la nube
        }
        anuncio.setAutorId(usuarioId != null ? usuarioId.intValue() : null);
        Anuncio creado = anuncioService.crearAnuncio(usuarioId, rol, aulaId, anuncio);
        return new ResponseEntity<>(creado, HttpStatus.CREATED);
    }
}
