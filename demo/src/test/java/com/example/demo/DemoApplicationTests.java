package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.demo.repo.categoriaRepo;
import com.example.demo.repo.comentarioRepo;
import com.example.demo.repo.imagenRepo;
import com.example.demo.repo.productoRepo;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class DemoApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private productoRepo productoRepo;

	@Autowired
	private categoriaRepo categoriaRepo;

	@Autowired
	private comentarioRepo comentarioRepo;

	@Autowired
	private imagenRepo imagenRepo;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void cleanUp() {
		categoriaRepo.deleteAll();
		comentarioRepo.deleteAll();
		imagenRepo.deleteAll();
		productoRepo.deleteAll();
	}

	@Test
	void contextLoads() {
	}

	@Test
	void flujoCompletoManyToManyProductoYCategoria() throws Exception {
		// 1. POST /producto
		String producto1Payload = """
				{
				  "nombre": "Proteína de fresa",
				  "descripcion": "2.2 kg",
				  "lote": "123456",
				  "precioUnitario": 1500.5,
				  "fechaCaducidad": "2026-05-04"
				}
				""";

		MvcResult p1Res = mockMvc.perform(post("/producto")
				.contentType(MediaType.APPLICATION_JSON)
				.content(producto1Payload))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").isString())
				.andExpect(jsonPath("$.nombre").value("Proteína de fresa"))
				.andExpect(jsonPath("$.deparments").doesNotExist())
				.andReturn();

		String producto1Id = objectMapper.readTree(p1Res.getResponse().getContentAsString()).get("id").asText();
		assertThat(UUID.fromString(producto1Id)).isNotNull();

		String producto2Payload = """
				{
				  "nombre": "Creatina Monohidratada",
				  "descripcion": "500 g",
				  "lote": "789012",
				  "precioUnitario": 650.0,
				  "fechaCaducidad": "2027-01-15"
				}
				""";

		MvcResult p2Res = mockMvc.perform(post("/producto")
				.contentType(MediaType.APPLICATION_JSON)
				.content(producto2Payload))
				.andExpect(status().isOk())
				.andReturn();

		String producto2Id = objectMapper.readTree(p2Res.getResponse().getContentAsString()).get("id").asText();

		// 2. POST /categoria (crear dos categorías)
		String categoria1Payload = """
				{
				  "nombre": "Suplementos",
				  "descripcion": "Suplementos alimenticios, vitaminas y productos naturistas",
				  "tipo": "Principal",
				  "icono": "<i class='bi bi-capsule-pill'></i>",
				  "imagenNavbar": "https://example.com/suplementos.jpg"
				}
				""";

		MvcResult c1Res = mockMvc.perform(post("/categoria")
				.contentType(MediaType.APPLICATION_JSON)
				.content(categoria1Payload))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").isString())
				.andExpect(jsonPath("$.nombre").value("Suplementos"))
				.andReturn();

		String categoria1Id = objectMapper.readTree(c1Res.getResponse().getContentAsString()).get("id").asText();
		assertThat(UUID.fromString(categoria1Id)).isNotNull();

		String categoria2Payload = """
				{
				  "nombre": "Ofertas",
				  "descripcion": "Productos con descuento",
				  "tipo": "Promocion",
				  "icono": "<i class='bi bi-tag'></i>",
				  "imagenNavbar": "https://example.com/ofertas.jpg"
				}
				""";

		MvcResult c2Res = mockMvc.perform(post("/categoria")
				.contentType(MediaType.APPLICATION_JSON)
				.content(categoria2Payload))
				.andExpect(status().isOk())
				.andReturn();

		String categoria2Id = objectMapper.readTree(c2Res.getResponse().getContentAsString()).get("id").asText();

		// 3. POST /addProductoCategoria/{productId}/{categoriaId} -> relación muchos-a-muchos
		mockMvc.perform(post("/addProductoCategoria/{productId}/{categoriaId}", producto1Id, categoria1Id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(categoria1Id))
				.andExpect(jsonPath("$.productos.length()").value(1));

		mockMvc.perform(post("/addProductoCategoria/{productId}/{categoriaId}", producto2Id, categoria1Id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.productos.length()").value(2));

		mockMvc.perform(post("/addProductoCategoria/{productId}/{categoriaId}", producto1Id, categoria2Id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.productos.length()").value(1));

		// 4. GET /categoria/producto/{productId} -> producto1 pertenece a 2 categorías
		mockMvc.perform(get("/categoria/producto/{productId}", producto1Id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));

		// 5. GET /categorias y GET /categoria/{id}
		mockMvc.perform(get("/categorias"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));

		mockMvc.perform(get("/categoria/{id}", categoria1Id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Suplementos"))
				.andExpect(jsonPath("$.productos.length()").value(2));

		// 6. GET /productos -> @JsonIgnore evita recursión infinita (no expone deparments)
		mockMvc.perform(get("/productos"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].deparments").doesNotExist());

		// 7. PUT /categoria/{id} y DELETE /categoria/{id}
		String actualizarCategoriaPayload = """
				{
				  "nombre": "Ofertas de Temporada",
				  "descripcion": "Descuentos especiales",
				  "tipo": "Promocion",
				  "icono": "<i class='bi bi-stars'></i>",
				  "imagenNavbar": "https://example.com/ofertas2.jpg"
				}
				""";

		mockMvc.perform(put("/categoria/{id}", categoria2Id)
				.contentType(MediaType.APPLICATION_JSON)
				.content(actualizarCategoriaPayload))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Ofertas de Temporada"));

		mockMvc.perform(delete("/categoria/{id}", categoria2Id))
				.andExpect(status().isOk())
				.andExpect(content().json("{\"success\":true}"));

		assertThat(categoriaRepo.findAll()).hasSize(1);
		assertThat(productoRepo.findAll()).hasSize(2);
	}

	@Test
	void esquemaCreaTablaIntermediaProductoDeparmentsConColumnasYClavesForaneas() throws Exception {
		try (Connection conn = dataSource.getConnection()) {
			DatabaseMetaData meta = conn.getMetaData();

			Set<String> joinColumns = new HashSet<>();
			try (ResultSet rs = meta.getColumns(null, null, "PRODUCTO_DEPARMENTS", null)) {
				while (rs.next()) {
					joinColumns.add(rs.getString("COLUMN_NAME").toLowerCase());
				}
			}
			assertThat(joinColumns).containsExactlyInAnyOrder("product_id", "deparments_id");

			Set<String> referencedTables = new HashSet<>();
			try (ResultSet rs = meta.getImportedKeys(null, null, "PRODUCTO_DEPARMENTS")) {
				while (rs.next()) {
					referencedTables.add(rs.getString("PKTABLE_NAME").toLowerCase());
				}
			}
			assertThat(referencedTables).containsExactlyInAnyOrder("categorias", "producto");
		}
	}
}
