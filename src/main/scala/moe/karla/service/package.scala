package moe.karla

package object service:
  
  extension [A <: String] (str: A)
    def filtered = 
      str.strip()
        .replace('?', '？')
        .replace('（', '(')
        .replace('）', ')')
        .replace(": ", "：")
        .replaceAll("[\\\\/:*?\"<>|]", " ")
