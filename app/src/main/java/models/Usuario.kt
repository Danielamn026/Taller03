package models

data class Usuario(
    val nombre: String = "",
    val apellido: String = "",
    val correo: String = "",
    val identificacion: String = "",
    val latitud: Double = 0.0,
    val longitud: Double = 0.0,
    val imagenUrl: String = "",
    val disponibilidad: String = ""
) {
    constructor(nom: String, apellido: String, correo: String, latitud: Double, longitud: Double, imagenUrl: String) :
            this(
                nombre = nom,
                apellido = apellido,
                correo = correo,
                latitud = latitud,
                longitud = longitud,
                imagenUrl = imagenUrl,
            )
}