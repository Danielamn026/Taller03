package models

data class Usuario(
    val nombre: String = "",
    val apellido: String = "",
    val email: String = "",
    val identificacion: String = "",
    val latitud: Double = 0.0,
    val longitud: Double = 0.0,
    val imagenUrl: String = "" // URL del Storage
)
