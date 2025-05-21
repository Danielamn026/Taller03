package adapters

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.taller03.MapaUsuariosActivity
import com.example.taller03.R
import com.example.taller03.databinding.ItemUsuarioBinding
import com.squareup.picasso.Picasso
import models.Usuario

class UsuarioAdapter(private val context: Context, private val usuarios: List<Usuario>) :
    RecyclerView.Adapter<UsuarioAdapter.UsuarioViewHolder>() {

    class UsuarioViewHolder(val binding: ItemUsuarioBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UsuarioViewHolder {
        val binding = ItemUsuarioBinding.inflate(LayoutInflater.from(context), parent, false)
        return UsuarioViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UsuarioViewHolder, position: Int) {
        val usuario = usuarios[position]
        with(holder.binding) {
            tvNombre.text = "${usuario.nombre} ${usuario.apellido}"
            if (!usuario.imagenUrl.isNullOrEmpty()) {
                Glide.with(context)
                    .load(usuario.imagenUrl)
                    .placeholder(R.drawable.circle_gray)
                    .error(R.drawable.error_image)
                    .circleCrop()
                    .into(imagenPerfil)

            } else {
                imagenPerfil.setImageResource(R.drawable.error_image)
            }

            btnUbicacion.setOnClickListener {
                val intent = Intent(context, MapaUsuariosActivity::class.java)
                intent.putExtra("correo", "${usuario.correo}")
                intent.putExtra("nombre", "${usuario.nombre}")
                context.startActivity(intent)
            }
        }
    }

    override fun getItemCount(): Int = usuarios.size
}


