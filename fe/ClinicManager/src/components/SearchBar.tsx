import React, { useEffect, useRef } from 'react'
import { Search } from 'lucide-react'
import { useState, useCallback } from 'react'

export interface SearchBarProps {
  onKeywordChange: (keyword: string) => void;
  resetTriggered?: boolean;
  initialValue?: string;
  placeholder?: string;
  className?: string;
}

export function SearchBar ({
  onKeywordChange,
  resetTriggered,
  initialValue = "",
  placeholder = "Szukaj...",
  className="",
}: SearchBarProps) {

  const [keyword, setKeyword] = useState<string>(initialValue);
  const inputRef = useRef<HTMLInputElement>(null);
  const lastResetRef = useRef<boolean | undefined>(resetTriggered);

  const handleInputChange = useCallback((event: React.ChangeEvent<HTMLInputElement>) => {
    const newKeyword = event.target.value;
    setKeyword(newKeyword);

    onKeywordChange(newKeyword);
  }, [onKeywordChange]);

  useEffect(() => {
      if (lastResetRef.current === resetTriggered) return;
      lastResetRef.current = resetTriggered;
      setKeyword("");
      onKeywordChange("");
  }, [resetTriggered]);

  return (
    <div className={`searchbar-container flex align-self-center g-05 ${className}`}>
        <Search
          className="searchbar-icon align-self-center pointer"
          onClick={() => inputRef.current?.focus()}
        />
        <input
          ref={inputRef}
          className="search-bar-stock align-self-center border-none"
          placeholder={placeholder}
          value={keyword}
          onChange={handleInputChange}
          />
    </div>
  )
}

export default SearchBar
