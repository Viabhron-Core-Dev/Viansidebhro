const fs = require('fs');
const file = 'app/src/main/java/com/example/service/FloatingReaderService.kt';
let text = fs.readFileSync(file, 'utf8');

// Update instantiations
text = text.replace(
    'listLibrary?.adapter = FileAdapter(sortedFiles)',
    `listLibrary?.adapter = FileAdapter(
        sortedFiles,
        { file -> 
            explorerStack.add(file)
            saveLibraryState()
            loadLibraryBooks()
        },
        { file ->
            showToast("Importing \${file.name}...")
            serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val repo = com.example.data.LibraryRepository(this@FloatingReaderService)
                val book = repo.importBook(android.net.Uri.fromFile(file))
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (book != null) {
                        loadBook(book.id)
                        hideOverlays()
                    } else {
                        showToast("Failed to import")
                    }
                }
            }
        },
        { file -> showExplorerContextMenu(file) },
        { file -> loadEpubCover(file) },
        serviceScope
    )`
);

text = text.replace(
    'listLibrary?.adapter = LibraryAdapter(books)',
    `listLibrary?.adapter = LibraryAdapter(
        books,
        { book ->
            if (book.isParsed) {
                loadBook(book.id)
                hideOverlays()
            } else {
                showToast("Book is still parsing...")
            }
        },
        { book ->
            serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val db = com.example.data.AppDatabase.getDatabase(this@FloatingReaderService)
                db.epubDao().deleteBook(book)
                // Just reload the library entirely
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    loadLibraryBooks()
                }
            }
            showToast("Deleted \${book.title}")
        }
    )`
);

text = text.replace(
    'notesAdapter = NotesAdapter()',
    `notesAdapter = NotesAdapter(
        notesList,
        selectedNotes,
        { updateNotesUi() },
        { note -> showNoteDialog(note) }
    )`
);

text = text.replace(
    'listBookmarks?.adapter = BookmarkAdapter()',
    `listBookmarks?.adapter = BookmarkAdapter(
        bookmarksList,
        { chapter, offset -> loadAndJumpToOffset(chapter, offset) },
        { floatingView.findViewById<android.view.View>(com.example.R.id.overlay_bookmarks)?.visibility = android.view.View.GONE }
    )`
);

text = text.replace(
    'listChapters?.adapter = ChapterAdapter(count)',
    `listChapters?.adapter = ChapterAdapter(
        count,
        currentChapterIndex,
        { pos ->
            saveCurrentPosition()
            currentChapterIndex = pos
            currentBook?.let { book ->
                currentBook = book.copy(lastReadChapter = pos, lastReadScrollY = 0)
            }
            loadChapterText()
            hideOverlays()
        }
    )`
);

// Delete the inner classes accurately
function removeBlock(startStr, endStr) {
    let startIndex = text.indexOf(startStr);
    if (startIndex !== -1) {
        let endIndex = text.indexOf(endStr, startIndex) + endStr.length;
        text = text.substring(0, startIndex) + text.substring(endIndex);
    }
}

removeBlock(
    "private inner class FileAdapter(var files: List<java.io.File>)",
    "override fun getItemCount() = files.size\n    }"
);

removeBlock(
    "private inner class LibraryAdapter(var books: List<com.example.data.EpubBook>)",
    "override fun getItemCount() = books.size\n    }"
);

removeBlock(
    "private inner class NotesAdapter :",
    "override fun getItemCount() = notesList.size\n    }"
);

removeBlock(
    "private data class BookmarkItem(val words: String, val chapter: Int, val percentage: Int)",
    "percentage: Int)"
);

removeBlock(
    "private inner class BookmarkAdapter :",
    "override fun getItemCount() = bookmarksList.size\n    }"
);

removeBlock(
    "private inner class ChapterAdapter(val totalChapters: Int)",
    "override fun getItemCount() = totalChapters\n    }"
);

fs.writeFileSync(file, text);
console.log("Refactored accurately.");
