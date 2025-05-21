package models

data class Usuario(
    val nombre: String = "",
    val apellidos: String = "",
    val correo: String = "",
    val identificacion: String = "",
    val latitud: Double = 0.0,
    val longitud: Double = 0.0,
    val imagenUrl: String = "",
    val disponibilidad: String = ""
) {
    constructor(nom: String, apellidos: String, correo: String, latitud: Double, longitud: Double, imagenUrl: String) :
            this(
                nombre = nom,
                apellidos = apellidos,
                correo = correo,
                latitud = latitud,
                longitud = longitud,
                imagenUrl = imagenUrl,
            )
}