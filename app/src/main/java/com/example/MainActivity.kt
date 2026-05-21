package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.database.HushDatabase
import com.example.data.repository.HushRepository
import com.example.ui.viewmodel.HushViewModel
import com.example.ui.screens.HushMainScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup direct state graph and offline architecture
        val database = HushDatabase.getDatabase(applicationContext)
        val repository = HushRepository(
            database.userDao(),
            database.contactDao(),
            database.statusDao(),
            database.statusReplyDao()
        )
        
        val viewModel: HushViewModel by viewModels {
            HushViewModelFactory(repository)
        }
        
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                HushMainScreen(viewModel)
            }
        }
    }
}

class HushViewModelFactory(private val repository: HushRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HushViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HushViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

